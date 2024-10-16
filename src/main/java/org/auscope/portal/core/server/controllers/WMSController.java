package org.auscope.portal.core.server.controllers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import javax.naming.OperationNotSupportedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.auscope.portal.core.server.http.HttpServiceCaller;
import org.auscope.portal.core.services.PortalServiceException;
import org.auscope.portal.core.services.WMSService;
import org.auscope.portal.core.services.responses.wms.GetCapabilitiesRecord;
import org.auscope.portal.core.services.responses.wms.GetCapabilitiesWMSLayerRecord;
import org.auscope.portal.core.util.FileIOUtil;
import org.auscope.portal.core.view.ViewCSWRecordFactory;
import org.auscope.portal.core.view.ViewGetCapabilitiesFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handles GetCapabilites (WFS)WMS queries.
 *
 * @author Jarek Sanders
 * @version $Id$
 */
@Controller
@Scope("session")
//this can't be a singleton as each request by a user may be targeting a specific wms version
public class WMSController extends BaseCSWController {
	
    // ----------------------------------------------------- Instance variables

    private WMSService wmsService;
    private final Log log = LogFactory.getLog(getClass());
    protected static int BUFFERSIZE = 1024 * 1024;
    HttpServiceCaller serviceCaller;

    // ----------------------------------------------------------- Constructors
    @Autowired
    public WMSController(WMSService wmsService, ViewCSWRecordFactory viewCSWRecordFactory, 
        ViewGetCapabilitiesFactory viewGetCapabilitiesFactory, HttpServiceCaller serviceCaller) {
        super(viewCSWRecordFactory);
        this.wmsService = wmsService;
        this.serviceCaller = serviceCaller;
    }

    /**
	 * Gets the GetCapabilities response for the given WMS URL
	 *
	 * @param serviceUrl The WMS URL to query
	 */
	@RequestMapping("/getWMSCapabilities.do")
	public ModelAndView getWmsCapabilities(@RequestParam("serviceUrl") String serviceUrl,
			@RequestParam("version") String version) throws Exception {
		try {
			GetCapabilitiesRecord capabilitiesRec = wmsService.getWmsCapabilities(serviceUrl, version);
			return generateJSONResponseMAV(true, capabilitiesRec, "");
		} catch (Exception e) {
			log.warn(String.format("Unable to retrieve WMS GetCapabilities for '%1$s'", serviceUrl));
			log.debug(e);
			return generateJSONResponseMAV(false, "Unable to process request", null);
		}
	}
	
	/**
	 * Gets the GetCapabilities response for a supplied WMS URL via a proxy.
	 * Note this returns the raw response rather than a JSON Object.
	 * 
	 * @param response the Response Object
	 * @param request the Request Object
	 * @param url the service URL
	 * @param version the WMS version
	 * @param usePost if true use a POST request, else use a GET 
	 * @param useWhitelist if true verify the url is on the whitelist before allowing request
	 * @throws PortalServiceException
	 * @throws OperationNotSupportedException
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	@RequestMapping(value = "/getWMSCapabilitiesViaProxy.do", method = {RequestMethod.GET, RequestMethod.POST})
    public void getViaProxy(
            HttpServletResponse response,
            HttpServletRequest request,
            @RequestParam("url") String url,
            @RequestParam("version") String version,
            @RequestParam(required = false, value = "usepostafterproxy", defaultValue = "false") boolean usePost,
            @RequestParam(required = false, value = "usewhitelist", defaultValue = "true") boolean useWhitelist
            ) throws PortalServiceException, OperationNotSupportedException, URISyntaxException, IOException {
		this.wmsService.getWMSCapabilitiesViaProxy(response, request, url, version, usePost, useWhitelist);
	}

    /**
     * Gets all the valid GetMap formats that a service defines
     *
     * @param serviceUrl
     *            The WMS URL to query
     */
    @RequestMapping("/getLayerFormats.do")
    public ModelAndView getLayerFormats(@RequestParam("serviceUrl") String serviceUrl) throws Exception {
        try {

            GetCapabilitiesRecord capabilitiesRec = wmsService.getWmsCapabilities(serviceUrl, null);

            List<ModelMap> data = new ArrayList<ModelMap>();
            for (String format : capabilitiesRec.getGetMapFormats()) {
                ModelMap formatItem = new ModelMap();
                formatItem.put("format", format);
                data.add(formatItem);
            }

            return generateJSONResponseMAV(true, data, "");
        } catch (Exception e) {
            log.warn(String.format("Unable to download WMS layer formats for '%1$s'", serviceUrl));
            log.debug(e);
            return generateJSONResponseMAV(false, "Unable to process request", null);
        }
    }

    /**
     * Gets the Metadata URL from the getCapabilities record if it is defined there.
     *
     * @param serviceUrl The WMS URL to query
     * @param version the WMS version
     * @param name the layer name
     */
    @RequestMapping("/getWMSLayerMetadataURL.do")
    public ModelAndView getWMSLayerMetadataURL(
            @RequestParam("serviceUrl") String serviceUrl,
            @RequestParam("version") String wmsVersion,
            @RequestParam("name") String name) throws Exception {

        try {
            /*
             * It might be preferable to create a nicer way of getting the data for the specific layer
             * This implementation just loops through the whole capabilities document looking for the layer.
             */
            String decodedServiceURL = URLDecoder.decode(serviceUrl, "UTF-8");

            GetCapabilitiesRecord getCapabilitiesRecord =
                    wmsService.getWmsCapabilities(decodedServiceURL, wmsVersion);

            String metadataURL = null;

            for (GetCapabilitiesWMSLayerRecord layer : getCapabilitiesRecord.getLayers()) {
                if (name.equals(layer.getTitle())) {
                    metadataURL = layer.getMetadataURL();
                    break;
                }
            }

            // if no value was found in a child layer then use the value in the parent record.
            if (StringUtils.isBlank(metadataURL)) {
                metadataURL = getCapabilitiesRecord.getMetadataUrl();
            }

            return generateJSONResponseMAV(true, metadataURL, "");

        } catch (Exception e) {
            log.warn(String.format("Unable to download WMS MetadataURL for '%1$s'", serviceUrl));
            log.debug(e);
            return generateJSONResponseMAV(false, "", null);
        }
    }

    /**
     *
     * @param request
     * @param response
     * @param wmsUrl
     * @param latitude
     * @param longitude
     * @param queryLayers
     * @param x
     * @param y
     * @param bbox
     *            A CSV string formatted in the form - longitude,latitude,longitude,latitude
     * @param width
     * @param height
     * @param infoFormat
     * @param sld_body
     *            - sld_body
     * @param postMethod
     *            Use getfeatureinfo POST method rather then GET
     * @param version
     *            - the wms version to use
     * @throws Exception
     */
    @RequestMapping(value = "/wmsMarkerPopup.do", method = {RequestMethod.GET, RequestMethod.POST})
    public void wmsUnitPopup(HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam("serviceUrl") String serviceUrl,
            @RequestParam("lat") String latitude,
            @RequestParam("lng") String longitude,
            @RequestParam("QUERY_LAYERS") String queryLayers,
            @RequestParam("x") String x,
            @RequestParam("y") String y,
            @RequestParam("BBOX") String bbox,
            @RequestParam("WIDTH") String width,
            @RequestParam("HEIGHT") String height,
            @RequestParam("INFO_FORMAT") String infoFormat,
            @RequestParam(value = "SLD_BODY", defaultValue = "") String sldBody,
            @RequestParam(value = "postMethod", defaultValue = "false") Boolean postMethod,
            @RequestParam("version") String version,
            @RequestParam(value = "feature_count", defaultValue = "0") String feature_count) throws Exception {

        String[] bboxParts = bbox.split(",");
        double lng1 = Double.parseDouble(bboxParts[0]);
        double lng2 = Double.parseDouble(bboxParts[2]);
        double lat1 = Double.parseDouble(bboxParts[1]);
        double lat2 = Double.parseDouble(bboxParts[3]);

        String responseString = wmsService.getFeatureInfo(serviceUrl, infoFormat, queryLayers, "EPSG:3857",
                Math.min(lng1, lng2), Math.min(lat1, lat2), Math.max(lng1, lng2), Math.max(lat1, lat2),
                Integer.parseInt(width), Integer.parseInt(height), Double.parseDouble(longitude),
                Double.parseDouble(latitude),
                (int) (Double.parseDouble(x)), (int) (Double.parseDouble(y)), "", sldBody, postMethod, version,
                feature_count, true);
        //VT: Ugly hack for the GA wms layer in registered tab as its font is way too small at 80.
        //VT : GA style sheet also mess up the portal styling of tables as well.
        if (responseString.contains("table, th, td {")) {
            responseString = responseString.replaceFirst("table, th, td \\{",
                    ".ausga table, .ausga th, .ausga td {");
            responseString = responseString.replaceFirst("th, td \\{", ".ausga th, .ausga td {");
            responseString = responseString.replaceFirst("th \\{", ".ausga th {");
            responseString = responseString.replace("<table", "<table class='ausga'");
        }

        InputStream responseStream = new ByteArrayInputStream(responseString.getBytes());
        FileIOUtil.writeInputToOutputStream(responseStream, response.getOutputStream(), BUFFERSIZE, true);
    }
    /**
    * get the default style for polygon Layer
    * @param response
    * @param layerName 
    * 			the layName 
    * @throws Exception
    */
    @RequestMapping("/getDefaultPolygonStyle.do")
    public void getDefaultPolygonStyle( 
            HttpServletResponse response,    		
            @RequestParam(required = false, value = "layerName") String layerName,
            @RequestParam(required = false, value = "colour") Integer colour)
                    throws Exception {
        if (colour == null) {
            colour = 0xed9c38; 
        }
        String hexColour="#" + Integer.toHexString(colour);    	
        String style = this.getStyle(layerName, hexColour, "POLYGON");

        response.setContentType("text/xml");

        ByteArrayInputStream styleStream = new ByteArrayInputStream(
                style.getBytes());
        OutputStream outputStream = response.getOutputStream();

        FileIOUtil.writeInputToOutputStream(styleStream, outputStream, 1024, false);

        styleStream.close();
        outputStream.close();
    }


    /**
     * Gets the LegendURL from the getCapabilities record if it is defined there.
     * TODO I think this should be the default but at the moment it is not being used at all.
     *
     * @param serviceUrl The WMS URL to query
     */
    @RequestMapping("/getLegendURL.do")
    public ModelAndView getLegendURL(
            @RequestParam("serviceUrl") String serviceUrl,
            @RequestParam("wmsVersion") String wmsVersion,
            @RequestParam("layerName") String layerName) throws Exception {

        try {
            /*
             * It might be preferable to create a nicer way of getting the data for the specific layer
             * This implementation just loops through the whole capabilities document looking for the layer.
             */
            GetCapabilitiesRecord getCapabilitiesRecord =
                    wmsService.getWmsCapabilities(serviceUrl, wmsVersion);

            String url = null;

            for (GetCapabilitiesWMSLayerRecord layer : getCapabilitiesRecord.getLayers()) {
                if (layerName.equals(layer.getName())) {
                    url = layer.getLegendURL();
                    break;
                }
            }
            return generateJSONResponseMAV(true, url, "");

        } catch (Exception e) {
            log.warn(String.format("Unable to download WMS legendURL for '%1$s'", serviceUrl));
            log.debug(e);
            return generateJSONResponseMAV(false, "", null);
        }
    }

    /**
    * get the default style for point Layer
    * @param response
    * @param layerName 
    * 			the layName 
    * @throws Exception
    */    
    @RequestMapping("/getDefaultStyle.do")
    public void getDefaultStyle(
            HttpServletResponse response,
            @RequestParam("layerName") String layerName,
            @RequestParam(required = false, value = "colour") Integer colour)
                    throws Exception {
        if (colour == null) {
            colour = 0xed9c38; 
        }
        String hexColour="#" + Integer.toHexString(colour);
        String style = this.getStyle(layerName, hexColour, "POINT");

        response.setContentType("text/xml");

        ByteArrayInputStream styleStream = new ByteArrayInputStream(
                style.getBytes());
        OutputStream outputStream = response.getOutputStream();

        FileIOUtil.writeInputToOutputStream(styleStream, outputStream, 1024, false);

        styleStream.close();
        outputStream.close();
    }

    public String getStyle(String name, String color, String spatialType) {
        // VT : This is a hack to get around using functions in feature chaining
        // https://jira.csiro.au/browse/SISS-1374
        // there are currently no available fix as wms request are made prior to
        // knowing app-schema mapping.
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<StyledLayerDescriptor version=\"1.0.0\" xmlns:mo=\"http://xmlns.geoscience.gov.au/minoccml/1.0\" xmlns:er=\"urn:cgi:xmlns:GGIC:EarthResource:1.1\" xsi:schemaLocation=\"http://www.opengis.net/sld StyledLayerDescriptor.xsd\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:gsml=\"urn:cgi:xmlns:CGI:GeoSciML:2.0\" xmlns:sld=\"http://www.opengis.net/sld\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<NamedLayer>" + "<Name>" + name + "</Name>" + "<UserStyle>" + "<Name>portal-style</Name>" + "<Title>"
                + name + "</Title>" + "<Abstract>Portal-Default-Style</Abstract>" + "<IsDefault>1</IsDefault>"
                + "<FeatureTypeStyle>";
        String tail = "</FeatureTypeStyle>" + "</UserStyle>" + "</NamedLayer>" + "</StyledLayerDescriptor>";

        String rule = "";

        switch (spatialType) {
        case "POLYGON":
            rule = "<Rule>" + "<Name>AuscopeDefaultPolygon</Name>" + "<PolygonSymbolizer>" + "<Fill>"
                    + "<CssParameter name=\"fill\">" + color + "</CssParameter>"
                    + "<CssParameter name=\"fill-opacity\">0.4</CssParameter>" + "</Fill>" + "<Stroke>"
                    + "<CssParameter name=\"stroke\">" + color + "</CssParameter>"
                    + "<CssParameter name=\"stroke-width\">0.1</CssParameter>" + "</Stroke>" + "</PolygonSymbolizer>"
                    + "</Rule>";
            break;
        case "POINT":
        default:
            rule = "<Rule>" + "<Name>AuscopeDefaultPoint</Name>" + "<Abstract>" + name + "</Abstract>" + "<PointSymbolizer>"
                    + "<Graphic>" + "<Mark>" + "<WellKnownName>circle</WellKnownName>" + "<Fill>"
                    + "<CssParameter name=\"fill\">" + color + "</CssParameter>"
                    + "<CssParameter name=\"fill-opacity\">0.4</CssParameter>" + "</Fill>" + "<Stroke>"
                    + "<CssParameter name=\"stroke\">" + color + "</CssParameter>"
                    + "<CssParameter name=\"stroke-width\">1</CssParameter>" + "</Stroke>" + "</Mark>"
                    + "<Size>8</Size>" + "</Graphic>" + "</PointSymbolizer>" + "</Rule>";
            break;
        }
        String style = header + rule + tail;
        return style;
    }
}
