package org.open4goods.xwiki.services;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.open4goods.xwiki.config.UrlManagementHelper;
import org.open4goods.xwiki.config.XWikiConstantsRelations;
import org.open4goods.xwiki.config.XWikiConstantsResourcesPath;
import org.open4goods.xwiki.config.XWikiServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.xwiki.rest.model.jaxb.Attachment;
import org.xwiki.rest.model.jaxb.Attachments;
import org.xwiki.rest.model.jaxb.ObjectSummary;
import org.xwiki.rest.model.jaxb.Objects;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.rest.model.jaxb.PageSummary;
import org.xwiki.rest.model.jaxb.Pages;
import org.xwiki.rest.model.jaxb.SearchResult;
import org.xwiki.rest.model.jaxb.SearchResults;


/**
 * This service handles XWiki READ rest services 
 * 
 * @author Thierry.Ledan
 */            

public class XWikiReadService {

	private static Logger LOGGER = LoggerFactory.getLogger(XWikiReadService.class);
	
	private XWikiServiceProperties xWikiProperties;
	private XWikiConstantsResourcesPath resourcesPathManager;
	private MappingService mappingService;
	private UrlManagementHelper urlHelper;
	
	public XWikiReadService (MappingService mappingService, XWikiServiceProperties xWikiProperties) throws Exception {
		
		this.xWikiProperties = xWikiProperties;
		this.mappingService = mappingService;
		this.resourcesPathManager = new XWikiConstantsResourcesPath(xWikiProperties.getBaseUrl(), xWikiProperties.getApiEntrypoint(), xWikiProperties.getApiWiki());
		this.urlHelper = new UrlManagementHelper(xWikiProperties);
		
		
		//		TODO: check that wiki exists !!
		
//		// get all available wikis and check that the targeted one exists
//		if( xWikiProperties.getApiWiki() == null || ! helper.checkWikiExists(xWikiProperties.getApiWiki()) ) {
//			throw new Exception("The targeted wiki '" + xWikiProperties.getApiWiki() + "' does not exist");
//		}
	}
	
//	public String getBaseUrl() {
//		return resourcesPathManager.getBaseUrl();
//	}
	
	/**
	 * Request the xwiki rest api to GET a xwiki Page resource from space path and page name.
	 * Then create a XWiki Page Object from the rest response.
	 * 
	 * @param spacePath path to the Page resource
	 * @param pageName name of the page resource
	 * @return the Page object from GET request 
	 * 
	 */
	public Page getPage(String spacePath, String pageName) throws ResponseStatusException {
		// replace '.' with '/spaces/' to get all nested spaces if needed
		String pathTopage = spacePath.replace(".", "/spaces/");
		String endpoint = resourcesPathManager.getPageEndpoint(pathTopage, pageName);	
		return this.mappingService.mapPage(endpoint);
	}

	/**
	 * Request the xwiki rest api to GET all xwiki Page summaries resources from space path.
	 * Then create a XWiki Pages Object from the rest response.
	 * A Pages object contains a set of Page summaries.
	 * 
	 * @param spacePath targeted path
	 * @return the Pages object from GET request 
	 * 
	 */
	public Pages getPages(String spacePath) throws ResponseStatusException {
		// replace '.' with '/spaces/' to get all nested spaces if needed
		String pathToPages = spacePath.replace(".", "/spaces/");
		String endpoint = resourcesPathManager.getPagesEndpoint(pathToPages);
		return this.mappingService.mapPages(endpoint);
	}
		
	/**
	 * Retrieve all 'Page' associated to a space
	 * with properties and attachments (disabled as default)
	 * 
	 * @param spacePath
	 * @return A List of 'Page' object, could be empty, never null
	 */
	public List<Page> getPagesList(String spacePath) throws ResponseStatusException {
		
		Pages pages = null;
		List<Page> pagesList = new ArrayList<Page>();
		// replace '.' with '/spaces/' to get all nested spaces if needed
		String pathTopage = spacePath.replace(".", "/spaces/");
		pages = this.mappingService.mapPages(resourcesPathManager.getPagesEndpoint(pathTopage));
		
		// Loop on PageSummary list in order to create Page list
		if( pages != null && !pages.getPageSummaries().isEmpty() ) {
			
			Page tempPage = null;
			
			for(PageSummary p: pages.getPageSummaries()) {
				
				// get page endpoint
				// TODO: add request param to url in order to get fields that are disabled by default
				String pageEndpoint =  urlHelper.getHref(XWikiConstantsRelations.REL_PAGE, p.getLinks());
				tempPage = this.mappingService.mapPage(pageEndpoint);

				if( tempPage != null ) {
					pagesList.add(tempPage);
					
					//--------------------------
					// fetch attachments
					//-------------------------
					try {
						Attachments attachments = this.mappingService.getAttachments(tempPage);
						if( attachments != null && attachments.getAttachments() != null && attachments.getAttachments().size()  > 0 ) {
							// update url (scheme, query params..) according to application properties
							for(Attachment attachment: attachments.getAttachments()) {
								attachment.setXwikiAbsoluteUrl(this.urlHelper.updateUrlScheme(attachment.getXwikiAbsoluteUrl()));
								attachment.setXwikiRelativeUrl(this.urlHelper.updateUrlScheme(attachment.getXwikiRelativeUrl()));
							}
							tempPage.setAttachments(attachments);
						}
					} catch( Exception e ) {
						// do not stop process, just log error and return page without attachments
						LOGGER.warn("Exception raised while getting attachments from Page {}", pageEndpoint );
					}

					//------------------------------------
					// fetch properties
					//------------------------------------
					try {
					} catch( Exception e ) {
						// do not stop process, just log error and return page without properties
					}
						
					//------------------------------------
					// fetch objects
					//------------------------------------
					try {
						Objects objects = this.mappingService.getPageObjects(tempPage);
						if( objects != null ) {
							tempPage.setObjects(objects);
						}
					} catch( Exception e ) {
						// do not stop process, just log error and return page without properties
						LOGGER.warn("Exception raised while getting properties from Page {}", pageEndpoint);
					}

					//------------------------------------
					// TODO: fetch classes
					//------------------------------------
					try {
					} catch( Exception e ) {
						// do not stop process, just log error and return page without attachments
					}
				}
			}
		} else {
			  throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No Page in space '" + pathTopage + "'");
		}	
		return pagesList;
	}
	
	
	/**
	 * Get properties related to Page with name 'pageName' in space 'spaceName'
	 * 
	 * @param spaceName space related to 'page'
	 * @param pageName name of 'page'
	 * @return
	 */
	public Map<String,String> getProperties(String spaces, String pageName) throws ResponseStatusException {
		Map<String,String> props = new HashMap<String, String>();
		// replace '.' with '/spaces/' to get all nested spaces if needed
		String spacesPath = spaces.replace(".", "/spaces/");
		String endpoint = resourcesPathManager.getPageEndpoint(spacesPath, pageName);
		Page page = this.mappingService.mapPage(endpoint);
		if(page != null) {
			props = this.mappingService.getProperties(page);
		}
		return props;
	}
	

	//////////////////////////////
	//							// 									
	//  USERS - GROUPS - ROLES	//
	//							// 
	//////////////////////////////
	
	/**
	 * Get all groups pageName
	 * discard "XWikiGroupTemplate"  
	 * TODO : XwikiAuthService
	 * @return
	 */
	public List<String> getGroupsName(){
		List<String> groups = new ArrayList<String>();
		SearchResults results = this.mappingService.mapSearchResults(resourcesPathManager.getGroupsEndpoint());
		if( results != null && !results.getSearchResults().isEmpty()) {
			for(SearchResult result: results.getSearchResults()) {
				if( !result.getPageName().contains("XWikiGroupTemplate") ) {
					groups.add(result.getPageName());
				}
			}
		}
		return groups;
	}
	
	
	/**
	 * Get users name for a group
	 * 
	 * scan objects summary, user's name is set in field "headline" with the prefix "XWiki."
	 * TODO : XwikiAuthenticationService
	 * @param groupPageName
	 * @return
	 */
	public List<String> getGroupUsers(String groupPageName) {
		// https://wiki.nudger.fr/rest/wikis/xwiki/spaces/XWiki/pages/SiteEditor/objects?media=json
		List<String> users = new ArrayList<String>();
		Objects objects = this.mappingService.getObjects(resourcesPathManager.getGroupUsers(groupPageName));
		if( objects != null && ! objects.getObjectSummaries().isEmpty() ) {
			for( ObjectSummary objectsummary: objects.getObjectSummaries() ) {
				if( ! objectsummary.getHeadline().isEmpty() ) {
					users.add(objectsummary.getHeadline().replaceAll("XWiki.", ""));
				}
			}
		}
		return users;
	}
	
	
	/**
	 * Get the User (Page object in xwiki) from username
	 * 
	 * @param userName
	 * @return
	 */
	public Page getUser(String userName) {
		Page page = null;
		String endpoint = resourcesPathManager.getPageEndpoint("", userName);	
		page = this.mappingService.mapPage(endpoint);
		return page;
	}
	
	

	
	/**
	 * Get the user's properties
	 * 
	 * @param userName
	 * @return
	 */
	public Map<String,String> getUserProperties(Page user) {
		Map<String,String> properties = new HashMap<String, String>();
		if( user != null ) {
			// first get objects from Page
			String propertiesUri = urlHelper.getHref(XWikiConstantsRelations.REL_OBJECT, user.getLinks());
			Objects objects = this.mappingService.getObjects(propertiesUri);
			// then get properties from objects (look for an object from class "XWikiUsers")
			properties =  this.mappingService.getUserProperties(objects, resourcesPathManager.getUsersClassName());
		}
		return properties;
	}

	/**
	 * Download the full XAR wiki file
	 * @param destFile
	 * @throws TechnicalException
	 * @throws InvalidParameterException
	 */
//	public void exportXwikiContent ( File destFile) {
//
//		// Optional Accept header
//		RequestCallback requestCallback = request -> {
//			try (OutputStreamWriter writer = new OutputStreamWriter(request.getBody(), StandardCharsets.UTF_8)) {
//				writer.write("name=all&description=&licence=&author=XWiki.Admin&version=&history=false&backup=true");
//
//			} catch(IOException ioe) {
//
//			} catch(Exception e) {
//
//			}
//		};
//
//		// Streams the response instead of loading it all in memory
//		ResponseExtractor<Void> responseExtractor = response -> {
//			// Here I write the response to a file but do what you like
//			Path path = Paths.get(destFile.getAbsolutePath());
//			Files.copy(response.getBody(), path);
//			return null;
//		};
//
//		if (destFile.exists()) {
//			destFile.delete();
//		}
//		
//		
//		restTemplate.execute(URI.create( resourcesPathManager.getBaseUrl() + "/xwiki/bin/export/XWiki/XWikiPreferences?editor=globaladmin&section=Export"), HttpMethod.POST, requestCallback, responseExtractor);
//	}

}
