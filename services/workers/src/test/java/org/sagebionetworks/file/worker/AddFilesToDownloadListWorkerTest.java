package org.sagebionetworks.file.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sagebionetworks.asynchronous.workers.sqs.MessageUtils;
import org.sagebionetworks.common.util.progress.ProgressCallback;
import org.sagebionetworks.repo.manager.UserManager;
import org.sagebionetworks.repo.manager.asynch.AsynchJobStatusManager;
import org.sagebionetworks.repo.manager.file.download.BulkDownloadManager;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.asynch.AsynchronousJobStatus;
import org.sagebionetworks.repo.model.asynch.AsynchronousResponseBody;
import org.sagebionetworks.repo.model.file.AddFileToDownloadListRequest;
import org.sagebionetworks.repo.model.file.AddFileToDownloadListResponse;
import org.sagebionetworks.repo.model.file.DownloadList;
import org.sagebionetworks.repo.model.table.Query;
import org.sagebionetworks.workers.util.aws.message.RecoverableMessageException;

import com.amazonaws.services.sqs.model.Message;

@ExtendWith(MockitoExtension.class)
public class AddFilesToDownloadListWorkerTest {

	@Mock
	AsynchJobStatusManager mockAsynchJobStatusManager;
	
	@Mock
	UserManager mockUserManager;
	
	@Mock
	BulkDownloadManager mockBulkDownloadManager;
	
	@Mock
	ProgressCallback mockProgressCallback;
	
	@Captor
	ArgumentCaptor<Throwable> exceptionCaptor; 
	
	@InjectMocks
	AddFilesToDownloadListWorker worker;
	
	UserInfo user;
	
	AddFileToDownloadListRequest addFolderRequest;
	AsynchronousJobStatus addFolderJobStatus;
	Message addFolderMessage;
	DownloadList addFolderDownloadList;
	
	AddFileToDownloadListRequest addQueryRequest;
	AsynchronousJobStatus addQueryJobStatus;
	Message addQueryMessage;
	DownloadList addQueryDownloadList;
	Query query;
	
	@BeforeEach
	public void before() throws Exception {
		boolean isAdmin = false;
		user = new UserInfo(isAdmin, 567L);
		
		// add folder
		addFolderRequest = new AddFileToDownloadListRequest();
		addFolderRequest.setFolderId("syn123");
		addFolderJobStatus = new AsynchronousJobStatus();
		addFolderJobStatus.setJobId("9999");
		addFolderJobStatus.setRequestBody(addFolderRequest);
		addFolderJobStatus.setStartedByUserId(user.getId());
		addFolderMessage = MessageUtils.buildMessage(addFolderJobStatus);
	
		addFolderDownloadList = new DownloadList();
		addFolderDownloadList.setEtag("addFolderList");
		addFolderDownloadList.setOwnerId(user.getId().toString());

		
		// add query
		addQueryRequest = new AddFileToDownloadListRequest();
		query = new Query();
		query.setSql("select * from syn123");
		addQueryRequest.setQuery(query);
		addQueryJobStatus = new AsynchronousJobStatus();
		addQueryJobStatus.setJobId("8888");
		addQueryJobStatus.setRequestBody(addQueryRequest);
		addQueryJobStatus.setStartedByUserId(user.getId());
		addQueryMessage = MessageUtils.buildMessage(addQueryRequest);

		addQueryDownloadList = new DownloadList();
		addQueryDownloadList.setEtag("addQueryList");
		addQueryDownloadList.setOwnerId(user.getId().toString());
	}
	
	@Test
	public void testRunAddFolder() throws RecoverableMessageException, Exception {
		when(mockUserManager.getUserInfo(user.getId())).thenReturn(user);
		when(mockAsynchJobStatusManager.lookupJobStatus(addFolderMessage.getBody())).thenReturn(addFolderJobStatus);
		when(mockBulkDownloadManager.addFilesFromFolder(user, addFolderRequest.getFolderId())).thenReturn(addFolderDownloadList);
		
		// call under test
		worker.run(mockProgressCallback, addFolderMessage);
		verify(mockUserManager).getUserInfo(user.getId());
		verify(mockBulkDownloadManager).addFilesFromFolder(user, addFolderRequest.getFolderId());
		verify(mockBulkDownloadManager, never()).addFilesFromQuery(any(), any(UserInfo.class), any(Query.class));
		AddFileToDownloadListResponse expectedResponse = new AddFileToDownloadListResponse();
		expectedResponse.setDownloadList(addFolderDownloadList);
		verify(mockAsynchJobStatusManager).setComplete(addFolderJobStatus.getJobId(), expectedResponse);
		verify(mockAsynchJobStatusManager, never()).setJobFailed(any(String.class), any(Throwable.class));
	}
	
	@Test
	public void testRunAddQuery() throws RecoverableMessageException, Exception {
		when(mockUserManager.getUserInfo(user.getId())).thenReturn(user);
		when(mockAsynchJobStatusManager.lookupJobStatus(addQueryMessage.getBody())).thenReturn(addQueryJobStatus);
		when(mockBulkDownloadManager.addFilesFromQuery(mockProgressCallback, user, query)).thenReturn(addQueryDownloadList);
		
		// call under test
		worker.run(mockProgressCallback, addQueryMessage);
		verify(mockUserManager).getUserInfo(user.getId());
		verify(mockBulkDownloadManager, never()).addFilesFromFolder(any(UserInfo.class), any(String.class));
		verify(mockBulkDownloadManager).addFilesFromQuery(mockProgressCallback, user, addQueryRequest.getQuery());
		AddFileToDownloadListResponse expectedResponse = new AddFileToDownloadListResponse();
		expectedResponse.setDownloadList(addQueryDownloadList);
		verify(mockAsynchJobStatusManager).setComplete(addQueryJobStatus.getJobId(), expectedResponse);
		verify(mockAsynchJobStatusManager, never()).setJobFailed(any(String.class), any(Throwable.class));
	}
	
	@Test
	public void testRunBothFolderAndQuery() throws RecoverableMessageException, Exception {
		when(mockAsynchJobStatusManager.lookupJobStatus(addQueryMessage.getBody())).thenReturn(addQueryJobStatus);
		
		// the folder and query should not be set
		addQueryRequest.setFolderId("syn123");
		addQueryRequest.setQuery(new Query());

		// call under test
		worker.run(mockProgressCallback, addQueryMessage);
		verify(mockAsynchJobStatusManager, never()).setComplete(any(String.class), any(AsynchronousResponseBody.class));
		verify(mockAsynchJobStatusManager).setJobFailed(any(String.class), exceptionCaptor.capture());
		assertEquals(AddFilesToDownloadListWorker.SET_EITHER_FOLDER_ID_OR_QUERY_BUT_NOT_BOTH,
				exceptionCaptor.getValue().getMessage());
	}
	
	@Test
	public void testRunBothFolderAndQueryNull() throws RecoverableMessageException, Exception {
		when(mockUserManager.getUserInfo(user.getId())).thenReturn(user);
		when(mockAsynchJobStatusManager.lookupJobStatus(addQueryMessage.getBody())).thenReturn(addQueryJobStatus);
		
		// the folder and query should not be set
		addQueryRequest.setFolderId(null);
		addQueryRequest.setQuery(null);;
		// call under test
		worker.run(mockProgressCallback, addQueryMessage);
		verify(mockAsynchJobStatusManager, never()).setComplete(any(String.class), any(AsynchronousResponseBody.class));
		verify(mockAsynchJobStatusManager).setJobFailed(any(String.class), exceptionCaptor.capture());
		assertEquals(AddFilesToDownloadListWorker.MUST_PROVIDE_EITHER_FOLDER_ID_OR_QUERY,
				exceptionCaptor.getValue().getMessage());
	}
	
	@Test
	public void testRunRecoverableException() throws RecoverableMessageException, Exception {
		when(mockUserManager.getUserInfo(user.getId())).thenReturn(user);
		when(mockAsynchJobStatusManager.lookupJobStatus(addQueryMessage.getBody())).thenReturn(addQueryJobStatus);
		
		// setup recoverable exception
		when(mockBulkDownloadManager.addFilesFromQuery(any(), any(UserInfo.class), any(Query.class))).thenThrow(new RecoverableMessageException());
		// call under test
		assertThrows(RecoverableMessageException.class, ()->{
			worker.run(mockProgressCallback, addQueryMessage);
		});
		verify(mockAsynchJobStatusManager, never()).setComplete(any(String.class), any(AsynchronousResponseBody.class));
		verify(mockAsynchJobStatusManager, never()).setJobFailed(any(String.class), any(Throwable.class));
	}
	
	@Test
	public void testRunNonRecoverableException() throws RecoverableMessageException, Exception {
		when(mockUserManager.getUserInfo(user.getId())).thenReturn(user);
		when(mockAsynchJobStatusManager.lookupJobStatus(addQueryMessage.getBody())).thenReturn(addQueryJobStatus);
		
		IllegalArgumentException exception = new IllegalArgumentException("not allowed");
		when(mockBulkDownloadManager.addFilesFromQuery(any(), any(UserInfo.class), any(Query.class))).thenThrow(exception);
		// call under test
		worker.run(mockProgressCallback, addQueryMessage);
		verify(mockAsynchJobStatusManager, never()).setComplete(any(String.class), any(AsynchronousResponseBody.class));
		verify(mockAsynchJobStatusManager).setJobFailed(addQueryJobStatus.getJobId(), exception);
	}
	
}
