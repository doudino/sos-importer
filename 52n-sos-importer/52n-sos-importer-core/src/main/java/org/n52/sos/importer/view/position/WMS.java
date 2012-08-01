package org.n52.sos.importer.view.position;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.geotools.data.ows.Layer;
import org.geotools.data.wms.WebMapServer;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.MapContent;
import org.geotools.map.WMSLayer;
import org.geotools.ows.ServiceException;
import org.geotools.referencing.CRS;
import org.geotools.swing.JMapPane;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

public class WMS {

	private static final Logger logger = Logger.getLogger(WMS.class);

	public static void set(JMapPane mapPane) {
		try {
			MapContent map = new MapContent();

			URL url = null;
			try {
				url = new URL("http://osmtud.dyndns.org/wms/?VERSION=1.1.0&REQUEST=GetCapabilities");
			} catch (MalformedURLException e) {
				//will not happen
			}
			/*
			 * @see http://docs.geotools.org/stable/userguide/extension/wms/wms.html#layer
			 */
			WebMapServer wms = null;
			wms = new WebMapServer(url);
			Layer layer = wms.getCapabilities().getLayerList().get(1);

			WMSLayer displayLayer = new WMSLayer( wms, layer );
			map.addLayer(displayLayer);

			// When first shown on screen it will display the layers.
			mapPane.setMapContent( map );
			// 										values: minX, maxX, minY, maxY, crs
			ReferencedEnvelope bounds = new ReferencedEnvelope(-180.0, 180.0, -90.0, 90.0, CRS.decode("EPSG:4326"));
			mapPane.setDisplayArea(bounds);
			//mapPane.setDisplayArea(map.getMaxBounds());

		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block generated on 04.07.2012 around 10:32:20
			logger.error(String.format("Exception thrown: %s",
					e1.getMessage()),
					e1);
		} catch (IllegalStateException e1) {
			// TODO Auto-generated catch block generated on 04.07.2012 around 10:32:20
			logger.error(String.format("Exception thrown: %s",
					e1.getMessage()),
					e1);
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block generated on 04.07.2012 around 10:32:20
			logger.error(String.format("Exception thrown: %s",
					e1.getMessage()),
					e1);
		} catch (IOException e1) {
			// TODO Auto-generated catch block generated on 04.07.2012 around 10:32:20
			logger.error(String.format("Exception thrown: %s",
					e1.getMessage()),
					e1);
		}
		// TODO Auto-generated constructor stub generated on 03.07.2012 around 16:37:07
		catch (ServiceException e) {
			// TODO Auto-generated catch block generated on 06.07.2012 around 15:30:48
			logger.error(String.format("Exception thrown: %s",
					e.getMessage()),
					e);
		} catch (MismatchedDimensionException e) {
			// TODO Auto-generated catch block generated on 27.07.2012 around 15:56:01
			logger.error(String.format("Exception thrown: %s",
						e.getMessage()),
					e);
		} catch (NoSuchAuthorityCodeException e) {
			// TODO Auto-generated catch block generated on 27.07.2012 around 16:32:48
			logger.error(String.format("Exception thrown: %s",
						e.getMessage()),
					e);
		} catch (FactoryException e) {
			// TODO Auto-generated catch block generated on 27.07.2012 around 16:32:48
			logger.error(String.format("Exception thrown: %s",
						e.getMessage()),
					e);
		} 
	}
}
