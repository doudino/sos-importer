package org.n52.sos.importer.model.position;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.n52.sos.importer.interfaces.Combination;
import org.n52.sos.importer.interfaces.Component;
import org.n52.sos.importer.interfaces.MissingComponentPanel;
import org.n52.sos.importer.model.table.Cell;
import org.n52.sos.importer.model.table.TableElement;
import org.n52.sos.importer.view.position.MissingEPSGCodePanel;

public class EPSGCode extends Component {

	private static final Logger logger = Logger.getLogger(EPSGCode.class);
	
	private TableElement tableElement;
	
	private String pattern;
	
	private int value = -1;

	public EPSGCode(TableElement tableElement, String pattern) {
		this.tableElement = tableElement;
		this.pattern = pattern;
	}
	
	public EPSGCode(int value) {
		this.value = value;
	}
	
	public void setValue(int value) {
		logger.info("Assign Value to " + this.getClass().getName());
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public void setTableElement(TableElement tableElement) {
		logger.info("Assign Column to " + this.getClass().getName());
		this.tableElement = tableElement;
	}

	public TableElement getTableElement() {
		return tableElement;
	}
	
	public EPSGCode forThis(Cell featureOfInterestPosition) {
		if (tableElement == null)
			return new EPSGCode(value);
		else {
			String epsgString = tableElement.getValueFor(featureOfInterestPosition);
			return EPSGCode.parse(epsgString);
		}
	}
	
	public void mark(Color color) {
		if (tableElement != null)
			tableElement.mark(color);
	}
	
	@Override 
	public String toString() {
		if (getTableElement() == null)
			return "EPSG-Code "  + getValue();
		else 
			return "EPSG-Code " + getTableElement();
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public String getPattern() {
		return pattern;
	}

	@Override
	public MissingComponentPanel getMissingComponentPanel(Combination c) {
		return new MissingEPSGCodePanel((Position)c);
	}
	
	public static EPSGCode parse(String s) {
		int value = Integer.valueOf(s);
		return new EPSGCode(value);
	}
	
}
