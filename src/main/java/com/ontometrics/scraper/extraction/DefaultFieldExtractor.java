package com.ontometrics.scraper.extraction;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.Tag;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a means of pulling fields out of a page.
 * <p>
 * This extractor will work on tables and on DLs. The tables have to have 2
 * columns (TDs). This will take the first as the label and the second as the
 * value. Same is done with the DLs: the DT is read as the label and the DD as
 * the value.
 * <p>
 * One way to use this tool is to just start with this default extractor and
 * then start removing the the elements that are not helping.
 * 
 * @author Rob
 */
public class DefaultFieldExtractor extends BaseExtractor implements FieldExtractor<DefaultFieldExtractor> {

	private static final Logger log = LoggerFactory.getLogger(DefaultFieldExtractor.class);

	private List<DesignatedField> fieldsToGet = new ArrayList<DesignatedField>();

	@Override
	public DefaultFieldExtractor url(URL url) {
		super.url(url);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ontometrics.scraper.extraction.FieldExtractor#getFields()
	 */
	@Override
	public List<Field> getFields() {
		List<Field> extractedFields = new ArrayList<Field>();
		extractedFields.addAll(extractFieldsFromULs());
		extractedFields.addAll(extractFieldsFromTables());
		extractedFields.addAll(extractFieldsFromDLs());
		extractedFields.addAll(extractDesignatedFields());
		return extractedFields;
	}

	private List<Field> extractDesignatedFields() {
		List<Field> extractedFields = new ArrayList<Field>();

		for (DesignatedField designatedField : this.fieldsToGet) {
			List<Element> elementWithValue = getSource().getAllElements(designatedField.getTagToGetValueFrom());
			Element firstElement = elementWithValue.get(0);
			String value = firstElement.getTextExtractor().toString();
			log.debug("looking for field: {}, value: {}", designatedField.getLabel(), value);
			extractedFields.add(new ScrapedField(designatedField.getLabel(), value));
		}

		return extractedFields;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ontometrics.scraper.extraction.BaseExtractor#source(com.ontometrics
	 * .scraper.extraction.HtmlExtractor)
	 */
	public DefaultFieldExtractor source(HtmlExtractor htmlExtractor) {
		super.source(htmlExtractor);
		return this;
	}

	@Override
	public DefaultFieldExtractor section(HtmlExtractor htmlExtractor) {
		super.section(htmlExtractor);
		return this;
	}

	private List<Field> extractFieldsFromDLs() {
		List<Field> extractedFields = new ArrayList<Field>();
		List<Element> dls = getSource().getAllElements(HTMLElementName.DL);
		for (Element dt : dls) {
			extractedFields.addAll(extractFieldsFromDL(dt.toString()));
		}
		return extractedFields;
	}

	private List<Field> extractFieldsFromTables() {
		List<Field> extractedFields = new ArrayList<Field>();
		List<Element> tables = getSource().getAllElements(HTMLElementName.TABLE);
		//log.debug("found {} tables to try and find fields in {}", tables.size(), getSource().toString());

		for (Element table : tables) {
			extractedFields.addAll(extractFieldsFromTable(table.toString()));
		}
		return extractedFields;
	}

	private List<Field> extractFieldsFromULs() {
		List<Field> extractedFields = new ArrayList<Field>();
		List<Element> lists = getSource().getAllElements(HTMLElementName.UL);
		log.debug("found {} ULs to try and find fields in", lists.size());
		for (Element list : lists) {
			extractedFields.addAll(extractFieldsFromUL(list.toString()));
		}
		return extractedFields;
	}

	private List<Field> extractFieldsFromTable(String html) {
		// log.debug("extracting fields from table: {}", html);
		List<Field> extractedFields = new ArrayList<Field>();
		Source source = new Source(html);
		source.fullSequentialParse();
		int cellCount = source.getAllElements(HTMLElementName.TD).size();
		int rowCount = source.getAllElements(HTMLElementName.TR).size();
		log.debug("found {} cells in {} rows", cellCount, rowCount);
		if (cellCount == (rowCount * 2)) {
			Field lastField = null;
			log.debug("cells.size: {}", cellCount);
			List<Element> cells = source.getAllElements(HTMLElementName.TD);
			for (int i = 0; i < cellCount; i++) {
				Element labelElement = cells.get(i);
				Element valueElement = cells.get(++i);
				String label = labelElement.getTextExtractor().toString().trim().replaceAll(":$", "");
				String value = getValueFieldText(valueElement);
				log.debug("found field: {}={}", label, value);
				if (StringUtils.isEmpty(label) && lastField != null) {
					lastField.addValue(value);
				} else {
					lastField = new ScrapedField(label, value);
					extractedFields.add(lastField);
				}
			}
		} else {
			List<String> headers = new ArrayList<String>();
			List<Element> rows = source.getAllElements(HTMLElementName.TR);
			for (Element row : rows) {
				List<Element> headerElements = row.getAllElements(HTMLElementName.TH);
				if (headerElements.size()>0){
					headers.clear();
				}
				for (Element headerElement : headerElements) {
					String header = headerElement.getTextExtractor().toString();
					headers.add(header);
					log.debug("header text: {}", header);
				}
				List<Element> cells = row.getAllElements(HTMLElementName.TD);
				for (int n = headers.size(); n < cells.size(); n++){
					headers.add("col" + n);
				}
				int index = 0;
				for (Element cell : cells) {
					String label = headers.get(index++);
					String value = getValueFieldText(cell);
					extractedFields.add(new ScrapedField(label, value));
				}
			}
		}
		return extractedFields;
	}

	private List<Field> extractFieldsFromDL(String html) {
		List<Field> extractedFields = new ArrayList<Field>();
		Source source = new Source(html);
		source.fullSequentialParse();
		List<Element> labels = source.getAllElements(HTMLElementName.DT);
		List<Element> values = source.getAllElements(HTMLElementName.DD);
		int cellCount = Math.min(labels.size(), values.size());
		for (int i = 0; i < cellCount; i++) {
			String label = labels.get(i).getTextExtractor().toString().trim().replaceAll(":$", "");
			Element valueElement = values.get(i);
			log.debug("looking at value element: {}", valueElement);
			String value = getValueFieldText(valueElement);
			extractedFields.add(new ScrapedField(label, value));
		}
		return extractedFields;
	}

	private Collection<? extends Field> extractFieldsFromUL(String html) {
		List<Field> extractedFields = new ArrayList<Field>();
		Source source = new Source(html);
		source.fullSequentialParse();
		List<Element> lis = source.getAllElements(HTMLElementName.LI);
		for (Element li : lis) {
			log.debug("looking at li: {} w/text: {}", li, li.getTextExtractor().toString());
			String[] parts = li.getTextExtractor().toString().split(":");
			if (parts.length == 2) {
				Field field = new ScrapedField(parts[0], parts[1]);
				extractedFields.add(field);
				log.debug("found <li> to process: {}, added field: {}", li, field);
			} else if (tagsWithSpecificTagRemoved(HTMLElementName.BR, li.getAllTags()).size() == 4) {
				Tag enclosingTag = li.getAllTags().get(1);
				log.info("enclosing tag: {}", enclosingTag);
				log.info("first element of enclosing tag: {}", enclosingTag.getElement().getTextExtractor().toString());
				String tagText = enclosingTag.getElement().getTextExtractor().toString().trim().replaceAll(":$", "");
				String allText = li.getTextExtractor().toString().trim().replaceAll(":$", "");
				log.info("enclosing tag text starts at: {}", allText.indexOf(tagText));
				log.debug("tagText (length = {}): {} alltext (length = {}): {}", new Object[] { tagText.length(),
						tagText, allText.length(), allText });
				if (allText.startsWith(tagText)) {
					String valueText = (allText.length() > tagText.length()) ? allText.substring(tagText.length() + 1)
							: "";
					extractedFields.add(new ScrapedField(tagText, valueText));
					log.debug("extracted fields = {}", extractedFields);
				}
			}

		}
		return extractedFields;
	}

	private List<Tag> tagsWithSpecificTagRemoved(String tagNameToRemove, List<Tag> tags) {
		log.debug("Tag name to remove = {}, tags to operate on = {}", tagNameToRemove, tags);
		for (int i = 0; i < tags.size(); i++) {
			Tag currentTag = tags.get(i);
			log.debug("Current tag name = {}", currentTag.getName());
			if (currentTag.getName() == tagNameToRemove) {
				tags.remove(currentTag);
				i--;
			}
		}
		log.debug("returning tags = {}", tags);
		return tags;
	}

	private String getValueFieldText(Element valueElement) {
		String result = "";
		List<Element> subElements = valueElement.getAllElements(HTMLElementName.A);
		if (subElements.size() > 0) {
			log.debug("found a tag inside field!");
			for (Element element : subElements) {
				if (element.getName().equals(HTMLElementName.A)) {
					if (element.getAttributeValue("href") != null) {
						log.debug("found href");
						result = element.getAttributeValue("href");
					}
				} else {
					result = element.getTextExtractor().toString();
				}
			}
		} else {
			result = valueElement.getTextExtractor().toString();
			log.debug("returning value = {}", result);
		}
		return result;
	}

	@Override
	public DefaultFieldExtractor field(String label, String element) {
		this.fieldsToGet.add(new DesignatedField(label, element));
		return this;
	}

}
