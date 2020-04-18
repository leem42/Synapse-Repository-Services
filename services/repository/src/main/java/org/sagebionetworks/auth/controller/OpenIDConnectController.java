package org.sagebionetworks.auth.controller;

import static org.sagebionetworks.repo.model.oauth.OAuthScope.openid;

import org.sagebionetworks.auth.HttpAuthUtil;
import org.sagebionetworks.repo.manager.oauth.OAuthClientNotVerifiedException;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.oauth.JsonWebKeySet;
import org.sagebionetworks.repo.model.oauth.OAuthAuthorizationResponse;
import org.sagebionetworks.repo.model.oauth.OAuthClient;
import org.sagebionetworks.repo.model.oauth.OAuthClientIdAndSecret;
import org.sagebionetworks.repo.model.oauth.OAuthClientList;
import org.sagebionetworks.repo.model.oauth.OAuthConsentGrantedResponse;
import org.sagebionetworks.repo.model.oauth.OAuthGrantType;
import org.sagebionetworks.repo.model.oauth.OIDCAuthorizationRequest;
import org.sagebionetworks.repo.model.oauth.OIDCAuthorizationRequestDescription;
import org.sagebionetworks.repo.model.oauth.OIDCTokenResponse;
import org.sagebionetworks.repo.model.oauth.OIDConnectConfiguration;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.repo.web.ServiceUnavailableException;
import org.sagebionetworks.repo.web.UrlHelpers;
import org.sagebionetworks.repo.web.controller.RequiredScope;
import org.sagebionetworks.repo.web.rest.doc.ControllerInfo;
import org.sagebionetworks.repo.web.service.ServiceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 
The OpenID Connect (OIDC) services implement OAuth 2.0 with the OpenID identity extensions.
 *
 */
@Controller
@ControllerInfo(displayName="OpenID Connect Services", path="auth/v1")
@RequestMapping(UrlHelpers.AUTH_PATH)
public class OpenIDConnectController {
	@Autowired
	private ServiceProvider serviceProvider;
	
	public static String getEndpoint(UriComponentsBuilder uriComponentsBuilder) {
		return uriComponentsBuilder.fragment(null).replaceQuery(null).path(UrlHelpers.AUTH_PATH).build().toString();	
	}

	/**
	 * Get the Open ID Configuration ("Discovery Document") for the Synapse OIDC service.
	 * @return
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.WELL_KNOWN_OPENID_CONFIGURATION, method = RequestMethod.GET)
	public @ResponseBody
	OIDConnectConfiguration getOIDCConfiguration(UriComponentsBuilder uriComponentsBuilder) throws NotFoundException {
		return serviceProvider.getOpenIDConnectService().
				getOIDCConfiguration(getEndpoint(uriComponentsBuilder));
	}
	
	/**
	 * Get the JSON Web Key Set for the Synapse OIDC service.  This is the set of public keys
	 * used to verify signed JSON Web tokens generated by Synapse.
	 * 
	 * @return the JSON Web Key Set
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_JWKS, method = RequestMethod.GET)
	public @ResponseBody
	JsonWebKeySet getOIDCJsonWebKeySet() {
		return serviceProvider.getOpenIDConnectService().
				getOIDCJsonWebKeySet();
	}
	
	/**
	 * Create an OAuth 2.0 client.  Note:  After creating the client one must also set the client secret
	 * and have their client verified (See the <a href="https://docs.synapse.org/articles/using_synapse_as_an_oauth_server.html">Synapse OAuth Server Documentation</a>)
	 * 
	 * @param oauthClient the client metadata for the new client
	 * @return
	 * @throws NotFoundException
	 * @throws ServiceUnavailableException if a sector identifer URI is registered but the file cannot be read
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CLIENT, method = RequestMethod.POST)
	public @ResponseBody
	OAuthClient createOAuthClient(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@RequestBody OAuthClient oauthClient
			) throws NotFoundException, ServiceUnavailableException {
		return serviceProvider.getOpenIDConnectService().
				createOpenIDConnectClient(userId, oauthClient);
	}
	
	/**
	 * Get a secret credential to use when requesting an access token.  
	 * <br>
	 * See the <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">Open ID Connect specification for client authentication</a>
	 * <br>
	 * Synapse supports 'client_secret_basic'.
	 * <br>
	 * <em>NOTE:  This request will invalidate any previously issued secrets.</em>
	 * 
	 * @param clientId the ID of the client whose secret is to be generated
	 * @return
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CLIENT_SECRET, method = RequestMethod.POST)
	public @ResponseBody 
	OAuthClientIdAndSecret createOAuthClientSecret(
		@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
		@PathVariable(value = UrlHelpers.ID_PATH_VARIABLE) String clientId) {
		return serviceProvider.getOpenIDConnectService().
				createOAuthClientSecret(userId, clientId);
	}
	
	/**
	 * Get an existing OAuth 2.0 client.  When retrieving one's own client,
	 * all metadata is returned.  It is permissible to retrieve a client anonymously
	 * or as a user other than the one who created the client, but only public fields
	 * (name, redirect URIs, and links to the client's site) are returned.
	 * 
	 * @param id the ID of the client to retrieve
	 * @return
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CLIENT_ID, method = RequestMethod.GET)
	public @ResponseBody
	OAuthClient getOAuthClient(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@PathVariable(value = UrlHelpers.ID_PATH_VARIABLE) String id
			) throws NotFoundException {
		return serviceProvider.getOpenIDConnectService().
				getOpenIDConnectClient(userId, id);
	}
	
	/**
	 * 
	 * List the OAuth 2.0 clients created by the current user.
	 * 
	 * @param userId
	 * @param nextPageToken returned along with a page of results, this is passed to 
	 * the server to retrieve the next page.
	 * @return
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CLIENT, method = RequestMethod.GET)
	public @ResponseBody
	OAuthClientList listOAuthClients(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@RequestParam(value = UrlHelpers.NEXT_PAGE_TOKEN_PARAM, required=false) String nextPageToken
			) throws NotFoundException {
		return serviceProvider.getOpenIDConnectService().
				listOpenIDConnectClients(userId, nextPageToken);
	}
	
	/**
	 * Update the metadata for an existing OAuth 2.0 client.
	 * Note, changing the redirect URIs will revert the 'verified' status of the client,
	 * necessitating re-verification.
	 * 
	 * @param oauthClient the client metadata to update
	 * @return
	 * @throws NotFoundException
	 * @throws ServiceUnavailableException 
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CLIENT_ID, method = RequestMethod.PUT)
	public @ResponseBody
	OAuthClient updateOAuthClient(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@RequestBody OAuthClient oauthClient
			) throws NotFoundException, ServiceUnavailableException {
		return serviceProvider.getOpenIDConnectService().
				updateOpenIDConnectClient(userId, oauthClient);
	}
	
	/**
	 * Delete OAuth 2.0 client
	 * 
	 * @param id the ID of the client to delete
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CLIENT_ID, method = RequestMethod.DELETE)
	public void deleteOpenIDClient(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@PathVariable(value = UrlHelpers.ID_PATH_VARIABLE) String id
			) throws NotFoundException {
		serviceProvider.getOpenIDConnectService().
				deleteOpenIDConnectClient(userId, id);
	}
	
	/**
	 * Get a user-readable description of the authentication request.
	 * <br>
	 * This request does not need to be authenticated.
	 * 
	 * @param authorizationRequest The request to be described
	 * @return
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_AUTH_REQUEST_DESCRIPTION, method = RequestMethod.POST)
	public @ResponseBody
	OIDCAuthorizationRequestDescription getAuthenticationRequestDescription(
			@RequestBody OIDCAuthorizationRequest authorizationRequest 
			) {
		return serviceProvider.getOpenIDConnectService().getAuthenticationRequestDescription(authorizationRequest);
	}
	
	/**
	 * Check whether user has already granted consent for the given OAuth client, scope, and claims.
	 * Consent persists for one year.
	 * 
	 * @param userId
	 * @param authorizationRequest The client, scope and claims for which the user may grant consent
	 * @return
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CONSENT_CHECK, method = RequestMethod.POST)
	public @ResponseBody
	OAuthConsentGrantedResponse checkUserAuthorization(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@RequestBody OIDCAuthorizationRequest authorizationRequest 
			) {
		OAuthConsentGrantedResponse result = new OAuthConsentGrantedResponse();
		result.setGranted(serviceProvider.getOpenIDConnectService().hasUserGrantedConsent(userId, authorizationRequest));
		return result;
	}
	
	/**
	 * 
	 * Get authorization code for a given client, scopes, response type(s), and extra claim(s).
	 * <br/>
	 * See:
	 * <br/>
	 * <a href="https://openid.net/specs/openid-connect-core-1_0.html#Consent">Open ID Connect specification for consent</a>.
	 * <br/>
	 * <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">Open ID Connect specification for the authorization request</a>.
	 *
	 * @param authorizationRequest the request to be authorized
	 * @return
	 * @throws NotFoundException
	 * @throws OAuthClientNotVerifiedException if the client is not verified
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = UrlHelpers.OAUTH_2_CONSENT, method = RequestMethod.POST)
	public @ResponseBody
	OAuthAuthorizationResponse authorizeClient(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@RequestBody OIDCAuthorizationRequest authorizationRequest 
			) throws NotFoundException, OAuthClientNotVerifiedException {
		return serviceProvider.getOpenIDConnectService().authorizeClient(userId, authorizationRequest);
	}
	
	/**
	 * 
	 *  Get access, refresh and id tokens, as per the 
	 *  <a href="https://openid.net/specs/openid-connect-core-1_0.html#TokenResponse">Open ID Connect specification for the token request</a>.
	 * <br/>
	 * <br/>
	 *  Request must include client ID and Secret in Basic Authentication header, i.e. the 'client_secret_basic' authentication method, as per the 
	 *  <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">Open ID Connect specification for client authentication</a>.
	 *  
	 * @param grant_type  authorization_code or refresh_token
	 * @param code required if grant_type is authorization_code
	 * @param redirectUri required if grant_type is authorization_code
	 * @param refresh_token required if grant_type is refresh_token
	 * @param scope only provided if grant_type is refresh_token
	 * @param claims optional if grant_type is refresh_token
	 * @return
	 * @throws NotFoundException
	 * @throws OAuthClientNotVerifiedException if the client is not verified
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = UrlHelpers.OAUTH_2_TOKEN, method = RequestMethod.POST)
	public @ResponseBody
	OIDCTokenResponse getTokenResponse(
			@RequestHeader(value = AuthorizationConstants.OAUTH_VERIFIED_CLIENT_ID_HEADER, required=true) String verifiedClientId,
			@RequestParam(value = AuthorizationConstants.OAUTH2_GRANT_TYPE_PARAM, required=true) OAuthGrantType grant_type,
			@RequestParam(value = AuthorizationConstants.OAUTH2_CODE_PARAM, required=false) String code,
			@RequestParam(value = AuthorizationConstants.OAUTH2_REDIRECT_URI_PARAM, required=false) String redirectUri,
			@RequestParam(value = AuthorizationConstants.OAUTH2_REFRESH_TOKEN_PARAM, required=false) String refresh_token,
			@RequestParam(value = AuthorizationConstants.OAUTH2_SCOPE_PARAM, required=false) String scope,
			@RequestParam(value = AuthorizationConstants.OAUTH2_CLAIMS_PARAM, required=false) String claims,
			UriComponentsBuilder uriComponentsBuilder
			)  throws NotFoundException, OAuthClientNotVerifiedException {
		return serviceProvider.getOpenIDConnectService().getTokenResponse(verifiedClientId, grant_type, code, redirectUri, refresh_token, scope, claims, getEndpoint(uriComponentsBuilder));
	}
		
	/**
	 * The result is either a JSON Object or a JSON Web Token, depending on whether the client registered a
	 * signing algorithm in its userinfo_signed_response_alg field, as per the
	 * <a href="https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata">Open ID Connect specification</a>.
	 * <br/>
	 * <br/>
	 * Authorization is via an OAuth access token passed as a Bearer token in the Authorization header.
	 * 
	 * @throws NotFoundException
	 * @throws OAuthClientNotVerifiedException if the client is not verified
	 */
	@RequiredScope({openid})
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_USER_INFO, method = {RequestMethod.GET})
	public @ResponseBody
	Object getUserInfoGET(
			@RequestHeader(value = AuthorizationConstants.SYNAPSE_AUTHORIZATION_HEADER_NAME, required=true) String authorizationHeader,
			UriComponentsBuilder uriComponentsBuilder
			)  throws NotFoundException, OAuthClientNotVerifiedException {
		String accessToken = HttpAuthUtil.getBearerTokenFromAuthorizationHeader(authorizationHeader);
		return serviceProvider.getOpenIDConnectService().getUserInfo(accessToken, getEndpoint(uriComponentsBuilder));
	}

	/**
	 * The result is either a JSON Object or a JSON Web Token, depending on whether the client registered a
	 * signing algorithm in its userinfo_signed_response_alg field, as per the
	 * <a href="https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata">Open ID Connect specification</a>.
	 * <br/>
	 * <br/>
	 * Authorization is via an OAuth access token passed as a Bearer token in the Authorization header.
	 * 
	 * @throws OAuthClientNotVerifiedException if the client is not verified
	 */
	@RequiredScope({openid})
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = UrlHelpers.OAUTH_2_USER_INFO, method = {RequestMethod.POST})
	public @ResponseBody
	Object getUserInfoPOST(
			@RequestHeader(value = AuthorizationConstants.SYNAPSE_AUTHORIZATION_HEADER_NAME, required=true) String authorizationHeader,
			UriComponentsBuilder uriComponentsBuilder
			)  throws NotFoundException, OAuthClientNotVerifiedException {
		String accessToken = HttpAuthUtil.getBearerTokenFromAuthorizationHeader(authorizationHeader);
		return serviceProvider.getOpenIDConnectService().getUserInfo(accessToken, getEndpoint(uriComponentsBuilder));
	}

}
