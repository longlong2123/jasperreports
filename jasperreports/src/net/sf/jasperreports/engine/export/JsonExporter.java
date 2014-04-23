/*
 * JasperReports - Free Java Reporting Library.
 * Copyright (C) 2001 - 2013 Jaspersoft Corporation. All rights reserved.
 * http://www.jaspersoft.com
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is part of JasperReports.
 *
 * JasperReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JasperReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JasperReports. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.jasperreports.engine.export;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRGenericPrintElement;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintHyperlink;
import net.sf.jasperreports.engine.JRPrintHyperlinkParameter;
import net.sf.jasperreports.engine.JRPrintHyperlinkParameters;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.PrintBookmark;
import net.sf.jasperreports.engine.ReportContext;
import net.sf.jasperreports.engine.util.HyperlinkData;
import net.sf.jasperreports.engine.util.JRValueStringUtils;
import net.sf.jasperreports.export.ExporterInputItem;
import net.sf.jasperreports.export.HtmlReportConfiguration;
import net.sf.jasperreports.export.JsonExporterConfiguration;
import net.sf.jasperreports.export.JsonReportConfiguration;
import net.sf.jasperreports.export.WriterExporterOutput;
import net.sf.jasperreports.web.util.JacksonUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * @author Lucian Chirita (lucianc@users.sourceforge.net)
 * @version $Id$
 */
public class JsonExporter extends JRAbstractExporter<JsonReportConfiguration, JsonExporterConfiguration, WriterExporterOutput, JsonExporterContext>
{
	
	private static final Log log = LogFactory.getLog(JsonExporter.class);
	
	public static final String JSON_EXPORTER_KEY = JRPropertiesUtil.PROPERTY_PREFIX + "json";
	
	protected static final String JSON_EXPORTER_PROPERTIES_PREFIX = JRPropertiesUtil.PROPERTY_PREFIX + "export.json.";
	
	protected Writer writer;
	protected int reportIndex;
	protected int pageIndex;
	private boolean gotFirstJsonFragment;
	
	public JsonExporter()
	{
		this(DefaultJasperReportsContext.getInstance());
	}

	public JsonExporter(JasperReportsContext jasperReportsContext)
	{
		super(jasperReportsContext);

		exporterContext = new ExporterContext();
	}


	/**
	 *
	 */
	protected Class<JsonExporterConfiguration> getConfigurationInterface()
	{
		return JsonExporterConfiguration.class;
	}


	/**
	 *
	 */
	protected Class<JsonReportConfiguration> getItemConfigurationInterface()
	{
		return JsonReportConfiguration.class;
	}
	

	/**
	 *
	 */
	@Override
	@SuppressWarnings("deprecation")
	protected void ensureOutput()
	{
		if (exporterOutput == null)
		{
			exporterOutput = 
				new net.sf.jasperreports.export.parameters.ParametersWriterExporterOutput(
					getJasperReportsContext(),
					getParameters(),
					getCurrentJasperPrint()
					);
		}
	}
	

	@Override
	public String getExporterKey()
	{
		return JSON_EXPORTER_KEY;
	}

	@Override
	public String getExporterPropertiesPrefix()
	{
		return JSON_EXPORTER_PROPERTIES_PREFIX;
	}

	@Override
	public void exportReport() throws JRException
	{
		/*   */
		ensureJasperReportsContext();
		ensureInput();

		//FIXMENOW check all exporter properties that are supposed to work at report level
		
		initExport();
		
		ensureOutput();

		writer = getExporterOutput().getWriter();

		try
		{
			exportReportToWriter();
		}
		catch (IOException e)
		{
			throw new JRException("Error writing to output writer : " + jasperPrint.getName(), e);
		}
		finally
		{
			getExporterOutput().close();
			resetExportContext();//FIXMEEXPORT check if using same finally is correct; everywhere
		}
	}
	
	@Override
	protected void initExport()
	{
		super.initExport();
	}
	
	@Override
	protected void initReport()
	{
		super.initReport();
	}
	
	protected void exportReportToWriter() throws JRException, IOException
	{
		writer.write("{\n");

		List<ExporterInputItem> items = exporterInput.getItems();

		for(reportIndex = 0; reportIndex < items.size(); reportIndex++)
		{
			ExporterInputItem item = items.get(reportIndex);

			setCurrentExporterInputItem(item);
			
			List<JRPrintPage> pages = jasperPrint.getPages();
			if (pages != null && pages.size() > 0)
			{
				PageRange pageRange = getPageRange();
				int startPageIndex = (pageRange == null || pageRange.getStartPageIndex() == null) ? 0 : pageRange.getStartPageIndex();
				int endPageIndex = (pageRange == null || pageRange.getEndPageIndex() == null) ? (pages.size() - 1) : pageRange.getEndPageIndex();

				JRPrintPage page = null;
				for(pageIndex = startPageIndex; pageIndex <= endPageIndex; pageIndex++)
				{
					if (Thread.interrupted())
					{
						throw new JRException("Current thread interrupted.");
					}

					page = pages.get(pageIndex);

					exportPage(page);

					if (reportIndex < items.size() - 1 || pageIndex < endPageIndex)
					{
						writer.write("\n");
					}
				}
			}
		}

		writer.write("\n}");

		boolean flushOutput = getCurrentConfiguration().isFlushOutput();
		if (flushOutput)
		{
			writer.flush();
		}
	}
	
	protected void exportPage(JRPrintPage page) throws IOException
	{
		Collection<JRPrintElement> elements = page.getElements();
		exportElements(elements);
		exportBookmarks();
		exportWebFonts();
		exportHyperlinks();

		JRExportProgressMonitor progressMonitor = getCurrentItemConfiguration().getProgressMonitor();
		if (progressMonitor != null)
		{
			progressMonitor.afterPageExport();
		}
	}
	
	protected void exportElements(Collection<JRPrintElement> elements) throws IOException
	{
		if (elements != null && elements.size() > 0)
		{
			for(Iterator<JRPrintElement> it = elements.iterator(); it.hasNext();)
			{
				JRPrintElement element = it.next();

				if (filter == null || filter.isToExport(element))
				{
					if (element instanceof JRPrintFrame)
					{
						exportFrame((JRPrintFrame)element);
					}
					else if (element instanceof JRGenericPrintElement)
					{
						exportGenericElement((JRGenericPrintElement) element);
					}
				}
			}
		}
	}
	
	protected void exportFrame(JRPrintFrame frame) throws IOException
	{
		exportElements(frame.getElements());
	}

	protected void exportBookmarks() throws IOException
	{
		List<PrintBookmark> bookmarks = jasperPrint.getBookmarks();
		if (bookmarks != null && bookmarks.size() > 0)
		{
			if (gotFirstJsonFragment)
			{
				writer.write(",\n");
			} else
			{
				gotFirstJsonFragment = true;
			}
			writer.write("\"bkmrk_" + (bookmarks.hashCode() & 0x7FFFFFFF) + "\": {");

			writer.write("\"id\": \"bkmrk_" + (bookmarks.hashCode() & 0x7FFFFFFF) + "\",");
			writer.write("\"type\": \"bookmarks\",");
			writer.write("\"bookmarks\": " + JacksonUtil.getInstance(getJasperReportsContext()).getJsonString(bookmarks));

			writer.write("}");
		}
	}

	protected void exportWebFonts() throws IOException
	{
		ReportContext reportContext = getReportContext();
		String webFontsParameter = "net.sf.jasperreports.html.webfonts";
		if (reportContext != null && reportContext.containsParameter(webFontsParameter)) {
			ArrayNode webFonts = (ArrayNode) reportContext.getParameterValue(webFontsParameter);
			if (gotFirstJsonFragment)
			{
				writer.write(",\n");
			} else
			{
				gotFirstJsonFragment = true;
			}
			writer.write("\"webfonts_" + (webFonts.hashCode() & 0x7FFFFFFF) + "\": {");

			writer.write("\"id\": \"webfonts_" + (webFonts.hashCode() & 0x7FFFFFFF) + "\",");
			writer.write("\"type\": \"webfonts\",");
			writer.write("\"webfonts\": " + JacksonUtil.getInstance(getJasperReportsContext()).getJsonString(webFonts));

			writer.write("}");
		}
	}

	protected void exportHyperlinks() throws IOException
	{
		ReportContext reportContext = getReportContext();
		String hyperlinksParameter = "net.sf.jasperreports.html.hyperlinks";
		if (reportContext != null && reportContext.containsParameter(hyperlinksParameter)) {
			List<HyperlinkData> hyperlinksData = (List<HyperlinkData>) reportContext.getParameterValue(hyperlinksParameter);
			if (hyperlinksData != null) {
				String id = "hyperlinks_" + (hyperlinksData.hashCode() & 0x7FFFFFFF);
				if (gotFirstJsonFragment)
				{
					writer.write(",\n");
				} else
				{
					gotFirstJsonFragment = true;
				}
				writer.write("\"" + id + "\": {");

				writer.write("\"id\": \"" + id + "\",");
				writer.write("\"type\": \"hyperlinks\",");
				writer.write("\"hyperlinks\": ");

				ObjectMapper mapper = new ObjectMapper();
				ArrayNode hyperlinkArray = mapper.createArrayNode();

				for (HyperlinkData hd: hyperlinksData) {
					ObjectNode hyperlinkNode = mapper.createObjectNode();
					JRPrintHyperlink hyperlink = hd.getHyperlink();

					addProperty(hyperlinkNode,"id", hd.getId());
					addProperty(hyperlinkNode, "href", hd.getHref());
					addProperty(hyperlinkNode, "selector", hd.getSelector());
					addProperty(hyperlinkNode, "type", hyperlink.getLinkType());
					addProperty(hyperlinkNode, "typeValue", hyperlink.getHyperlinkTypeValue().getName());
					addProperty(hyperlinkNode, "target", hyperlink.getLinkTarget());
					addProperty(hyperlinkNode, "targetValue", hyperlink.getHyperlinkTargetValue().getHtmlValue());
					addProperty(hyperlinkNode, "anchor", hyperlink.getHyperlinkAnchor());
					addProperty(hyperlinkNode, "page", String.valueOf(hyperlink.getHyperlinkPage()));
					addProperty(hyperlinkNode, "reference", hyperlink.getHyperlinkReference());

					JRPrintHyperlinkParameters hParams =  hyperlink.getHyperlinkParameters();
					if (hParams != null && hParams.getParameters().size() > 0) {
						ObjectNode params = mapper.createObjectNode();

						for (JRPrintHyperlinkParameter hParam: hParams.getParameters()) {
							if (hParam.getValue() != null) {
								if (Collection.class.isAssignableFrom(hParam.getValue().getClass())) {
									ArrayNode paramValues = mapper.createArrayNode();
									Collection col = (Collection) hParam.getValue();
									for (Iterator it = col.iterator(); it.hasNext();) {
										Object next = it.next();
										paramValues.add(JRValueStringUtils.serialize(next.getClass().getName(), next));
									}
									params.put(hParam.getName(), paramValues);
								} else {
									params.put(hParam.getName(), JRValueStringUtils.serialize(hParam.getValueClass(), hParam.getValue()));
								}
							}
						}

						hyperlinkNode.put("params", params);
					}

					hyperlinkArray.add(hyperlinkNode);
				}

				writer.write(JacksonUtil.getInstance(getJasperReportsContext()).getJsonString(hyperlinkArray));
				writer.write("}");
			}
		}
	}

	private void addProperty(ObjectNode objectNode, String property, String value) {
		if (value != null && !value.equals("null")) {
			objectNode.put(property, value);
		}
	}

	/**
	 *
	 */
	protected void exportGenericElement(JRGenericPrintElement element) throws IOException
	{
		GenericElementJsonHandler handler = (GenericElementJsonHandler) 
				GenericElementHandlerEnviroment.getInstance(getJasperReportsContext()).getElementHandler(
						element.getGenericType(), JSON_EXPORTER_KEY);
		
		if (handler != null)
		{
			String fragment = handler.getJsonFragment(exporterContext, element);
			if (fragment != null && !fragment.isEmpty()) {
				if (gotFirstJsonFragment) {
					writer.write(",\n");
				} else {
					gotFirstJsonFragment = true;
				}
				writer.write(fragment);
			}
		}
		else
		{
			if (log.isDebugEnabled())
			{
				log.debug("No JSON generic element handler for " 
						+ element.getGenericType());
			}
		}
	}
	
	/**
	 * 
	 */
	protected String resolveHyperlinkURL(int reportIndex, JRPrintHyperlink link)
	{
		String href = null;
		
		Boolean ignoreHyperlink = HyperlinkUtil.getIgnoreHyperlink(HtmlReportConfiguration.PROPERTY_IGNORE_HYPERLINK, link);
		if (ignoreHyperlink == null)
		{
			ignoreHyperlink = getCurrentItemConfiguration().isIgnoreHyperlink();
		}

		if (!ignoreHyperlink)
		{
			JRHyperlinkProducer customHandler = getHyperlinkProducer(link);		
			if (customHandler == null)
			{
				switch(link.getHyperlinkTypeValue())
				{
					case REFERENCE :
					{
						if (link.getHyperlinkReference() != null)
						{
							href = link.getHyperlinkReference();
						}
						break;
					}
					case LOCAL_ANCHOR :
					{
						if (link.getHyperlinkAnchor() != null)
						{
							href = "#" + link.getHyperlinkAnchor();
						}
						break;
					}
					case LOCAL_PAGE :
					{
						if (link.getHyperlinkPage() != null)
						{
							href = "#" + HtmlExporter.JR_PAGE_ANCHOR_PREFIX + reportIndex + "_" + link.getHyperlinkPage().toString();
						}
						break;
					}
					case REMOTE_ANCHOR :
					{
						if (
							link.getHyperlinkReference() != null &&
							link.getHyperlinkAnchor() != null
							)
						{
							href = link.getHyperlinkReference() + "#" + link.getHyperlinkAnchor();
						}
						break;
					}
					case REMOTE_PAGE :
					{
						if (
							link.getHyperlinkReference() != null &&
							link.getHyperlinkPage() != null
							)
						{
							href = link.getHyperlinkReference() + "#" + HtmlExporter.JR_PAGE_ANCHOR_PREFIX + "0_" + link.getHyperlinkPage().toString();
						}
						break;
					}
					case NONE :
					default :
					{
						break;
					}
				}
			}
			else
			{
				href = customHandler.getHyperlink(link);
			}
		}
		
		return href;
	}

	
	protected class ExporterContext extends BaseExporterContext implements JsonExporterContext
	{
		@Override
		public String getHyperlinkURL(JRPrintHyperlink link)
		{
			return resolveHyperlinkURL(reportIndex, link);
		}
	}

}
