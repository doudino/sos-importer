package org.n52.sos.importer.view.step3;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JLabel;

import org.apache.log4j.Logger;
import org.n52.sos.importer.Parseable;

public class ParseTestLabel extends JLabel {

	private static final Logger logger = Logger.getLogger(ParseTestLabel.class);
	
	private static final long serialVersionUID = 1L;
	
	private Parseable parser;
	
	public ParseTestLabel(Parseable parser) {
		super();
		this.parser = parser;
	}
	
	public void parseValues(List<String> values) {
		logger.info("Parse Values for " + parser.getClass().getName());
		int notParseableValues = 0;
		StringBuilder notParseable = new StringBuilder();
		Set<String> notParseableStrings = new HashSet<String>();
		notParseable.append("<html>");

		for (String value: values) {
			try {
				parser.parse(value);
			} catch (Exception e) {
				if (notParseableStrings.add(value))
					notParseable.append(value + "<br>");
				notParseableValues++;
			}
		}
		
		String text = "";
		if (notParseableValues == 0) {
			text = "All values parseable.";
			this.setForeground(Color.blue);
		} else if (notParseableValues == 1) {
			text = "1 value not parseable.";
			this.setForeground(Color.red);
		} else {
			text = notParseableValues + " values not parseable.";
			this.setForeground(Color.red);
		}
		
		this.setText("<html><u>" + text+ "</u></html>");
		
		notParseable.append("</html>");
		this.setToolTipText(notParseable.toString());
	}			
}