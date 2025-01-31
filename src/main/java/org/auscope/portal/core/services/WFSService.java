package org.auscope.portal.core.services;

import java.net.URISyntaxException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.auscope.portal.core.server.http.HttpServiceCaller;
import org.auscope.portal.core.services.methodmakers.WFSGetFeatureMethodMaker;
import org.auscope.portal.core.services.methodmakers.WFSGetFeatureMethodMaker.ResultType;
import org.auscope.portal.core.services.namespaces.ErmlNamespaceContext;
import org.auscope.portal.core.services.responses.ows.OWSExceptionParser;
import org.auscope.portal.core.services.responses.wfs.WFSResponse;
import org.auscope.portal.core.services.responses.wfs.WFSTransformedResponse;
import org.auscope.portal.core.xslt.GmlToHtml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * A service class encapsulating high level access to a remote Web Feature Service
 *
 * @author Josh Vote
 *
 */
@Service
public class WFSService extends BaseWFSService {

    private GmlToHtml gmlToHtml;

    /**
     * Creates a new instance of this class with the specified dependencies
     *
     * @param httpServiceCaller
     *            Will be used for making requests
     * @param wfsMethodMaker
     *            Will be used for generating WFS methods
     * @param gmlToKml
     *            Will be used for transforming GML (WFS responses) into KML
     * @param gmlToHtml
     *            Will be used for transforming GML (WFS responses) into HTML
     */
    @Autowired
    public WFSService(HttpServiceCaller httpServiceCaller,
            WFSGetFeatureMethodMaker wfsMethodMaker,
            GmlToHtml gmlToHtml) {
        super(httpServiceCaller, wfsMethodMaker);
        this.gmlToHtml = gmlToHtml;
    }

    /**
     * Sends out WFS request and returns its response
     * 
     * @param method 
     *         HttpRequestBase used to make the WFS request
     * @return 
     *         Response as WFSResponse object or PortalServiceException upon error
     * @throws PortalServiceException
     */
    protected WFSResponse doRequest(HttpRequestBase method)
            throws PortalServiceException {
        try {
            String wfs = httpServiceCaller.getMethodResponseAsString(method);
            OWSExceptionParser.checkForExceptionResponse(wfs);

            return new WFSResponse(wfs, method);
        } catch (Exception ex) {
            throw new PortalServiceException(method, ex);
        }
    }

    /**
     * Sends out WFS request and transforms its response to HTML
     * 
     * @param method 
     *          HttpRequestBase used to make the WFS request
     * @param baseUrl 
     *          The base URL of the request e.g.  https://portal.org/api
     * @return 
     *          Response as WFSTransformedResponse object or PortalServiceException upon error
     * @throws PortalServiceException
     */
    protected WFSTransformedResponse doRequestAndHtmlTransform(HttpRequestBase method, String baseUrl)
            throws PortalServiceException {
        try {
            String wfs = httpServiceCaller.getMethodResponseAsString(method);
            OWSExceptionParser.checkForExceptionResponse(wfs);
            return transformToHtml(wfs, method, baseUrl);
        } catch (Exception ex) {
            throw new PortalServiceException(method, ex);
        }
    }

    /**
	 * Transform WFS document into HTML format.
	 *
	 * @param wfs    GML feature string
	 * @param method 
     *          HttpRequestBase used to make the WFS request, or null if this
	 *          comes from WMS GetFeatureInfo popup.
     * @param baseUrl
     *          The base URL of the request e.g.  https://portal.org/api
	 * @return HTML converted response
	 */
    public WFSTransformedResponse transformToHtml(String wfs, HttpRequestBase method, String baseUrl) {
    	ErmlNamespaceContext erml;
        if (wfs.contains("http://xmlns.earthresourceml.org/EarthResource/2.0")) {
        	// Tell the XSLT which ERML version to use
        	erml = new ErmlNamespaceContext("2.0");
        } else {
        	erml = new ErmlNamespaceContext();
        }
    	String html = this.gmlToHtml.convert(wfs, erml, baseUrl);
    	return new WFSTransformedResponse(wfs, html, method);
    }

    /**
     * Makes a WFS GetFeature request constrained by the specified parameters
     *
     * The response is returned as a String
     *
     * @param wfsUrl
     *            the web feature service url
     * @param featureType
     *            the type name
     * @param featureId
     *            A unique ID of a single feature type to query
     * @return
     * @throws URISyntaxException
     * @throws Exception
     */
    public WFSResponse getWfsResponse(String wfsUrl, String featureType, String featureId)
            throws PortalServiceException, URISyntaxException {
        HttpRequestBase method = generateWFSRequest(wfsUrl, featureType, featureId, null, null, null, null);
        return doRequest(method);
    }

    /**
     * Makes a WFS GetFeature request constrained by the specified parameters
     *
     * The response is returned as a String
     *
     * @param wfsUrl
     *            the web feature service url
     * @param featureType
     *            the type name
     * @param filterString
     *            A OGC filter string to constrain the request
     * @param maxFeatures
     *            A maximum number of features to request
     * @param srs
     *            [Optional] The spatial reference system the response should be encoded to @param srsName - will use BaseWFSService.DEFAULT_SRS if unspecified
     * @return
     * @throws URISyntaxException
     * @throws Exception
     */
    public WFSResponse getWfsResponse(String wfsUrl, String featureType, String filterString,
            Integer maxFeatures, String srs) throws PortalServiceException, URISyntaxException {
        HttpRequestBase method = generateWFSRequest(wfsUrl, featureType, null, filterString, maxFeatures, srs,
                ResultType.Results);

        return doRequest(method);
    }

    /**
     * Makes a WFS GetFeature request constrained by the specified parameters
     *
     * The response is returned as a String in both GML and HTML forms.
     *

     * @param wfsUrl
     *            the web feature service URL
     * @param featureType
     *            the type name
     * @param featureId
     *            A unique ID of a single feature type to query
     * @param baseUrl
     *            The base URL of the request e.g.  https://portal.org/api
     * @return
     * @throws URISyntaxException
     * @throws Exception
     */
    public WFSTransformedResponse getWfsResponseAsHtml(String wfsUrl, String featureType, String featureId, String baseUrl)
    		throws PortalServiceException, URISyntaxException {
        HttpRequestBase method = generateWFSRequest(wfsUrl, featureType, featureId, null, null, null, null);
        return doRequestAndHtmlTransform(method, baseUrl);
    }

    /**
     * Makes a HTTP Get request to the specified URL.
     *
     * The response is returned as a String in both GML and HTML forms.
     *
     * @param wfsUrl
     *            the web feature service URL
     * @param baseUrl
     *            The base URL of the request e.g.  https://portal.org/api
     * @return
     * @throws Exception
     */
    public WFSTransformedResponse getWfsResponseAsHtml(String wfsUrl, String baseUrl) throws PortalServiceException {
        HttpRequestBase method = new HttpGet(wfsUrl);
        return doRequestAndHtmlTransform(method, baseUrl);
    }
}
