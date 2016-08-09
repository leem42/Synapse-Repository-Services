package org.sagebionetworks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sagebionetworks.client.DockerCommitSortBy;
import org.sagebionetworks.client.SharedClientConnection;
import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseAdminClientImpl;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.reflection.model.PaginatedResults;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.docker.DockerCommit;
import org.sagebionetworks.repo.model.docker.DockerRegistryEvent;
import org.sagebionetworks.repo.model.docker.DockerRegistryEventList;
import org.sagebionetworks.repo.model.docker.DockerRepository;
import org.sagebionetworks.repo.model.docker.RegistryEventAction;
import org.sagebionetworks.repo.model.docker.RegistryEventActor;
import org.sagebionetworks.repo.model.docker.RegistryEventRequest;
import org.sagebionetworks.repo.model.docker.RegistryEventTarget;
import org.sagebionetworks.schema.adapter.org.json.EntityFactory;

public class ITDocker {
	private static final String SCOPE_PARAM = "scope";
	private static final String SERVICE_PARAM = "service";
	private static final String DOCKER_AUTHORIZATION = "/bearerToken";

	private static final String TYPE = "repository";
	private static final String ACCESS_TYPES_STRING="push,pull";
	
	public static final String DOCKER_REGISTRY_EVENTS = "/events";

	private static SynapseAdminClient adminSynapse;
	private static SynapseClient synapseOne;
	private static Long userToDelete;
	private static String username;
	private static String password;

	private String projectId;

	private SharedClientConnection conn;

	@BeforeClass
	public static void beforeClass() throws Exception {
		// Create 2 users
		adminSynapse = new SynapseAdminClientImpl();
		SynapseClientHelper.setEndpoints(adminSynapse);
		adminSynapse
		.setUserName(StackConfiguration.getMigrationAdminUsername());
		adminSynapse.setApiKey(StackConfiguration.getMigrationAdminAPIKey());
		adminSynapse.clearAllLocks();
		synapseOne = new SynapseClientImpl();
		SynapseClientHelper.setEndpoints(synapseOne);

		username = UUID.randomUUID().toString();
		password = UUID.randomUUID().toString();
		userToDelete = SynapseClientHelper
				.createUser(adminSynapse, synapseOne, username, password);
	}

	@Before
	public void before() throws Exception {
		// get the underlying SharedClientConnection so we can add the basic
		// authentication header
		conn = synapseOne.getSharedClientConnection();

		Project project = new Project();
		project = synapseOne.createEntity(project);
		projectId = project.getId();
	}
	
	@After
	public void after() throws Exception {
		if (projectId!=null) synapseOne.deleteAndPurgeEntityById(projectId);
		projectId=null;
	}

	@AfterClass
	public static void afterClass() throws Exception {
		try {
			adminSynapse.deleteUser(userToDelete);
		} catch (SynapseException e) {
		}
	}
	
	private static String createBasicAuthorizationHeader(String username, String password) {
		return "Basic "+ (new String(Base64.
				encodeBase64((username + ":" + password).getBytes())));
	}

	@Test
	public void testDockerClientAuthorization() throws Exception {
		Map<String, String> requestHeaders = new HashMap<String, String>();
		// Note, without this header  we get a 415 response code
		requestHeaders.put("Content-Type", "application/json"); 
		requestHeaders.put(
				"Authorization",
				createBasicAuthorizationHeader(username, password));
		String service = "docker.synapse.org";
		String repoPath = projectId+"/reponame";
		String scope = TYPE+":"+repoPath+":"+ACCESS_TYPES_STRING;
		String urlString = StackConfiguration.getDockerServiceEndpoint() + DOCKER_AUTHORIZATION;
		urlString += "?" + SERVICE_PARAM + "=" + URLEncoder.encode(service, "UTF-8");
		urlString += "&" + SCOPE_PARAM + "=" + URLEncoder.encode(scope, "UTF-8");
		HttpResponse response = conn.performRequest(urlString, "GET", null,
				requestHeaders);
		
		HttpEntity httpEntity = response.getEntity();
		try {
			assertNotNull(EntityUtils.toString(httpEntity));

			assertEquals(HttpStatus.SC_OK, response.getStatusLine()
					.getStatusCode());
		} finally {
			EntityUtils.consumeQuietly(httpEntity);
		}
	}

	private static DockerCommit createCommit(String tag, String digest) {
		DockerCommit commit = new DockerCommit();
		Date createdOn = new Date();
		commit.setCreatedOn(createdOn);
		commit.setDigest(digest);
		commit.setTag(tag);
		return commit;
	}

	@Test
	public void testUnmanagedRepository() throws Exception {
		DockerRepository dockerRepo = new DockerRepository();
		dockerRepo.setParentId(projectId);
		dockerRepo.setRepositoryName("uname/reponame");
		dockerRepo = synapseOne.createEntity(dockerRepo);
		String tag = "tag";
		DockerCommit commit1 = createCommit(tag, UUID.randomUUID().toString());
		synapseOne.addDockerCommit(dockerRepo.getId(), commit1);
		Thread.sleep(10L);
		// now reassign the tag to a new commit
		DockerCommit commit2 = createCommit(tag, UUID.randomUUID().toString());
		synapseOne.addDockerCommit(dockerRepo.getId(), commit2);
		PaginatedResults<DockerCommit> result = synapseOne.listDockerCommits(dockerRepo.getId(), 10L, 0L, DockerCommitSortBy.TAG, true);
		assertEquals(1L, result.getTotalNumberOfResults());
		assertEquals(1, result.getResults().size());
		DockerCommit retrieved = result.getResults().get(0);
		assertNotNull(retrieved.getCreatedOn());
		assertEquals(commit2.getDigest(), retrieved.getDigest());
		assertEquals(tag, retrieved.getTag());

		// make sure optional params are optional
		assertEquals(
				result,
				synapseOne.listDockerCommits(dockerRepo.getId(), null, null, null, null)
				);
	}
	
	// helper function to construct registry events in the prescribed format
	private static DockerRegistryEventList createDockerRegistryEvent(
			RegistryEventAction action, String host, long userId, String repositoryPath, String tag, String digest) {
		DockerRegistryEvent event = new DockerRegistryEvent();
		event.setAction(action);
		RegistryEventRequest eventRequest = new RegistryEventRequest();
		event.setRequest(eventRequest);
		eventRequest.setHost(host);
		RegistryEventActor eventActor = new RegistryEventActor();
		event.setActor(eventActor);
		eventActor.setName(""+userId);
		RegistryEventTarget target = new RegistryEventTarget();
		target.setRepository(repositoryPath);
		target.setTag(tag);
		target.setDigest(digest);
		event.setTarget(target);
		DockerRegistryEventList eventList = new DockerRegistryEventList();
		List<DockerRegistryEvent> events = new ArrayList<DockerRegistryEvent>();
		eventList.setEvents(events);
		events.add(event);
		return eventList;
	}


	@Test
	public void testSendRegistryEvents() throws Exception {
		String registryUserName = StackConfiguration.getDockerRegistryUser();
		String registryPassword =StackConfiguration.getDockerRegistryPassword();
		Map<String, String> requestHeaders = new HashMap<String, String>();
		// Note, without this header  we get a 415 response code
		requestHeaders.put("Content-Type", "application/json"); 
		requestHeaders.put(
				"Authorization",
				createBasicAuthorizationHeader(registryUserName, registryPassword));
		String host = "docker.synapse.org";
		String repositorySuffix = "reponame";
		String repositoryPath = projectId+"/"+repositorySuffix;
		String tag = "latest";
		String digest = UUID.randomUUID().toString(); // usu. a SHA256, but not required
		DockerRegistryEventList registryEvents = createDockerRegistryEvent(
				RegistryEventAction.push,  host,  userToDelete,  repositoryPath,  tag,  digest);
		URL url = new URL(StackConfiguration.getDockerRegistryListenerEndpoint() + 
				DOCKER_REGISTRY_EVENTS);
		conn.performRequest(url.toString(), "POST",
				EntityFactory.createJSONStringForEntity(registryEvents),
				requestHeaders);

		// check that repo was created
		JSONObject queryResult = synapseOne.query("select id from dockerrepo where projectId == '"+projectId+"'");
		Long count = queryResult.getLong("totalNumberOfResults");
		assertEquals(new Long(1), count);

	}

	@Test
	public void testSendRegistryEventsWrongCredentials() throws Exception {
		Map<String, String> requestHeaders = new HashMap<String, String>();
		// Note, without this header  we get a 415 response code
		requestHeaders.put("Content-Type", "application/json"); 
		requestHeaders.put(
				"Authorization",
				createBasicAuthorizationHeader("wrong user name", "wrong password"));
		DockerRegistryEventList registryEvents = new DockerRegistryEventList();
		URL url = new URL(StackConfiguration.getDockerRegistryListenerEndpoint() + 
				DOCKER_REGISTRY_EVENTS);
		HttpResponse response = conn.performRequest(url.toString(), "POST",
				EntityFactory.createJSONStringForEntity(registryEvents),
				requestHeaders);
		
		HttpEntity httpEntity = response.getEntity();
		try {
			assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		} finally {
			EntityUtils.consumeQuietly(httpEntity);
		}
		
		JSONObject queryResult = synapseOne.query("select id from dockerrepo where projectId == '"+projectId+"'");
		Long count = queryResult.getLong("totalNumberOfResults");
		assertEquals(new Long(0), count);

	}


}