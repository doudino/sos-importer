/**
 * Copyright (C) 2011-2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sos.importer.feeder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;

import org.n52.oxf.xml.NcNameResolver;
import org.n52.sos.importer.feeder.csv.CsvParser;
import org.n52.sos.importer.feeder.csv.WrappedCSVReader;
import org.n52.sos.importer.feeder.exceptions.JavaApiBugJDL6203387Exception;
import org.n52.sos.importer.feeder.model.FeatureOfInterest;
import org.n52.sos.importer.feeder.model.ObservedProperty;
import org.n52.sos.importer.feeder.model.Offering;
import org.n52.sos.importer.feeder.model.Position;
import org.n52.sos.importer.feeder.model.Resource;
import org.n52.sos.importer.feeder.model.Sensor;
import org.n52.sos.importer.feeder.model.Timestamp;
import org.n52.sos.importer.feeder.model.UnitOfMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.x52North.sensorweb.sos.importer.x04.ColumnDocument.Column;
import org.x52North.sensorweb.sos.importer.x04.FeatureOfInterestType;
import org.x52North.sensorweb.sos.importer.x04.GeneratedResourceType;
import org.x52North.sensorweb.sos.importer.x04.GeneratedSpatialResourceType;
import org.x52North.sensorweb.sos.importer.x04.KeyDocument.Key;
import org.x52North.sensorweb.sos.importer.x04.ManualResourceType;
import org.x52North.sensorweb.sos.importer.x04.MetadataDocument.Metadata;
import org.x52North.sensorweb.sos.importer.x04.ObservedPropertyType;
import org.x52North.sensorweb.sos.importer.x04.RelatedObservedPropertyDocument.RelatedObservedProperty;
import org.x52North.sensorweb.sos.importer.x04.RelatedUnitOfMeasurementDocument.RelatedUnitOfMeasurement;
import org.x52North.sensorweb.sos.importer.x04.SensorType;
import org.x52North.sensorweb.sos.importer.x04.SpatialResourceType;
import org.x52North.sensorweb.sos.importer.x04.TypeDocument.Type;
import org.x52North.sensorweb.sos.importer.x04.UnitOfMeasurementType;

/**
 * Class holds the datafile and provides easy to use interfaces to get certain
 * required resources.
 *
 * @author <a href="mailto:e.h.juerrens@52north.org">Eike Hinderk J&uuml;rrens</a>
 * @version $Id: $Id
 */
public class DataFile {

    private static final String NAME = "name: %s";

    private static final String OS_NAME = "os.name";

    private static final Logger LOG = LoggerFactory.getLogger(DataFile.class);

    private static final int MILLIES_PER_HOUR = 1000 * 60 * 60;

    private final Configuration configuration;

    private final File dataFile;

    /**
     * <p>Constructor for DataFile.</p>
     *
     * @param configuration a {@link org.n52.sos.importer.feeder.Configuration} object.
     * @param file a {@link java.io.File} object.
     */
    public DataFile(final Configuration configuration, final File file) {
        this.configuration = configuration;
        this.dataFile = file;
    }

    /**
     * Checks if the dataFile is available and can be read. All errors like not
     * available, not a dataFile, and not readable are logged to
     * <code>LOG.error()</code>.
     *
     * @return <code>true</code>, if the Datafile is a dataFile and can be read,<br>
     *          else <code>false</code>.
     */
    public boolean isAvailable() {
        LOG.trace("isAvailable()");
        if (!dataFile.exists()) {
            LOG.error(String.format("File '%s' specified in '%s' does not exist.",
                    dataFile.getAbsolutePath(),
                    configuration.getConfigFile().getAbsolutePath()));
        } else if (!dataFile.isFile()) {
            LOG.error(String.format("File '%s' is not a file!",
                    dataFile.getAbsolutePath()));
        } else if (!dataFile.canRead()) {
            LOG.error(String.format("File '%s' can not be accessed, please check file permissions!",
                    dataFile.getAbsolutePath()));
        } else if (checkWindowsJavaApiBugJDK6203387(dataFile)) {
            LOG.error(
                    String.format("File '%s' can not be accessed, "
                            + "because another process blocked read access!",
                    dataFile.getAbsolutePath()));
            throw new JavaApiBugJDL6203387Exception(dataFile.getName());
        } else {
            LOG.debug(String.format("File '%s' is a file and read permission is available.",
                        dataFile.getAbsolutePath()));
            return true;
        }
        return false;
    }

    private boolean checkWindowsJavaApiBugJDK6203387(final File file) {
        if (isWindows()) {
            Reader fr = null;
            try {
                fr = new InputStreamReader(new FileInputStream(file), Configuration.DEFAULT_CHARSET);
            } catch (final FileNotFoundException fnfe) {
                // TODO add more language specific versions of this error message
                if (
                        (
                            fnfe.getMessage().indexOf("Der Prozess kann nicht auf die Datei zugreifen, "
                                    + "da sie von einem anderen Prozess verwendet wird") >= 0
                            ||
                            fnfe.getMessage().indexOf("The process cannot access the dataFile "
                                    + "because it is being used by another process") >= 0
                        )
                        &&
                        fnfe.getMessage().indexOf(file.getName()) >= 0) {
                    return true;
                }
            } catch (UnsupportedEncodingException e) {
                log(e);
            } finally {
                if (fr != null) {
                    try {
                        fr.close();
                    } catch (IOException e) {
                        log(e);
                    }
                }
            }
        }
        return false;
    }

    private void log(Exception e) {
        LOG.error("Exception thrown!", e);
        LOG.debug("Stackstrace", e);
    }

    private boolean isWindows() {
        return System.getProperty(OS_NAME).indexOf("Windows") >= 0 ||
                System.getProperty(OS_NAME).indexOf("windows") >= 0;
    }

    /**
     * Returns a CSVReader instance for the current DataFile using the configuration
     * including the defined values for: first line with data, separator, escape, and text qualifier.
     *
     * @return a <code>CSVReader</code> instance
     * @throws java.io.IOException if any.
     */
    public CsvParser getCSVReader() throws IOException {
        LOG.trace("getCSVReader()");
        final Reader fr = new InputStreamReader(new FileInputStream(dataFile), Configuration.DEFAULT_CHARSET);
        final BufferedReader br = new BufferedReader(fr);
        CsvParser cr = null;
        if (configuration.isCsvParserDefined()) {
            final String csvParser = configuration.getCsvParser();
            try {
                final Class<?> clazz = Class.forName(csvParser);
                final Constructor<?> constructor = clazz.getConstructor((Class<?>[]) null);
                final Object instance = constructor.newInstance();
                if (CsvParser.class.isAssignableFrom(instance.getClass())) {
                    cr = (CsvParser) instance;
                }
            } catch (final ClassNotFoundException |
                    NoSuchMethodException |
                    SecurityException |
                    InstantiationException |
                    IllegalAccessException |
                    IllegalArgumentException |
                    InvocationTargetException e) {
                final String errorMsg = String.format("Could not load defined CsvParser implementation class '%s'. "
                        + "Cancel import",
                        csvParser);
                LOG.error(errorMsg);
                LOG.debug("Exception thrown: {}", e.getMessage(), e);
                try {
                    br.close();
                } catch (final IOException e1) {
                    LOG.error("Could not close BufferedReader: {}", e1.getMessage(), e1);
                }
                throw new IllegalArgumentException(errorMsg, e);
            }
        }
        if (cr == null) {
            cr = new WrappedCSVReader();
        }
        cr.init(br, configuration);
        return cr;
    }

    /**
     * <p>getMeasuredValueColumnIds.</p>
     *
     * @see Configuration#getMeasureValueColumnIds()
     * @return an array of int.
     */
    public int[] getMeasuredValueColumnIds() {
        return configuration.getMeasureValueColumnIds();
    }

    /**
     * <p>getFirstLineWithData.</p>
     *
     * @see Configuration#getFirstLineWithData()
     * @return a int.
     */
    public int getFirstLineWithData() {
        return configuration.getFirstLineWithData();
    }

    /**
     * <p>getSensorForColumn.</p>
     *
     * @param mvColumnId a int.
     * @param values an array of {@link java.lang.String} objects.
     * @return a {@link Sensor} object.
     */
    public Sensor getSensorForColumn(final int mvColumnId, final String[] values) {
        LOG.trace("getSensorForColumn({},{})", mvColumnId, Arrays.toString(values));
        // check for sensor column and return new sensor
        Sensor sensor = getSensorFromColumn(mvColumnId, values);
        if (sensor == null) {
            LOG.debug("Could not find sensor column for column id {}", mvColumnId);
        } else {
            return sensor;
        }
        // else build sensor from manual or generated resource
        SensorType sensorType = configuration.getRelatedSensor(mvColumnId);
        // Case: one mv column => no related sensor element => check for one single sensor in additional metadata
        if (sensorType == null && configuration.isOneMvColumn()) {
            sensorType = configuration.getSensorFromAdditionalMetadata();
        }
        if (sensorType != null && sensorType.getResource() != null) {
            // generated sensor
            if (sensorType.getResource() instanceof GeneratedResourceType) {
                final GeneratedResourceType gRT = (GeneratedResourceType) sensorType.getResource();
                final String[] a = getUriAndNameFromGeneratedResourceType(
                        // concatstring
                        gRT.isSetConcatString()
                        ? gRT.getConcatString()
                                : null,
                                // uri
                                gRT.isSetURI()
                        ? gRT.getURI().getStringValue()
                                : null,
                                // useUriAsPrefix
                                gRT.isSetURI() &&
                                gRT.getURI().isSetUseAsPrefix()
                                ? gRT.getURI().getUseAsPrefix()
                                        : false,
                                        gRT.getNumberArray(),
                                        values
                        );
                sensor = new Sensor(a[0], a[1]);
            } else if (sensorType.getResource() instanceof ManualResourceType) {
                // manual sensor
                final ManualResourceType mRT = (ManualResourceType) sensorType.getResource();
                sensor = new Sensor(mRT.getName(),
                        mRT.getURI().getStringValue());
            }
        }
        return sensor;
    }

    /**
     * <p>getFoiForColumn.</p>
     *
     * @param mvColumnId a int.
     * @param values an array of {@link java.lang.String} objects.
     * @return a {@link org.n52.sos.importer.feeder.model.FeatureOfInterest} object.
     * @throws java.text.ParseException if any.
     */
    public FeatureOfInterest getFoiForColumn(final int mvColumnId, final String[] values) throws ParseException {
        LOG.trace(String.format("getFoiForColumn(%d,%s)",
                    mvColumnId,
                    Arrays.toString(values)));
        // check for foi column and return new foi
        FeatureOfInterest foi = getFoiColumn(mvColumnId, values);
        if (foi == null) {
            LOG.debug(String.format("Could not find foi column for column id %d",
                    mvColumnId));
        } else {
            if (foi.getPosition() == null) {
                LOG.debug(String.format("Found foi '%s' but no position for column %d",
                        foi.getName(),
                        mvColumnId));
            } else {
                return foi;
            }
        }
        // else build foi from manual or generated resource
        final FeatureOfInterestType foiT = configuration.getRelatedFoi(mvColumnId);
        if (foiT != null && foiT.getResource() != null) {
            // generated foi
            if (foiT.getResource() instanceof GeneratedSpatialResourceType) {
                final GeneratedSpatialResourceType gSRT =
                        (GeneratedSpatialResourceType) foiT.getResource();
                final String[] a = getUriAndNameFromGeneratedResourceType(
                        // concatstring
                        gSRT.isSetConcatString()
                        ? gSRT.getConcatString()
                                : null,
                                // uri
                                gSRT.isSetURI()
                                ? gSRT.getURI().getStringValue()
                                        : null,
                                        gSRT.isSetURI() &&
                                        // useUriAsPrefix
                                        gSRT.getURI().isSetUseAsPrefix()
                                        ? gSRT.getURI().getUseAsPrefix()
                                                : false,
                                                gSRT.getNumberArray(),
                                                values
                        );
                final Position p = getPosition(gSRT.getPosition(), values);
                foi = new FeatureOfInterest(a[0], a[1], p);
            } else if (foiT.getResource() instanceof SpatialResourceType) {
                // manual foi
                final SpatialResourceType mSRT = (SpatialResourceType) foiT.getResource();
                final Position p = getPosition(mSRT.getPosition(), values);
                foi = new FeatureOfInterest(mSRT.getName(),
                        mSRT.getURI().getStringValue(),
                        p);
            }
        }
        if (!NcNameResolver.isNCName(foi.getName())) {
            final String[] a = createCleanNCName(foi);
            foi.setName(a[0]);
            if (!a[0].equals(a[1])) {
                LOG.debug(String.format("Feature Of Interest name changed to match NCName production: '%s' to '%s'",
                        a[1],
                        a[0]));
            }
        }
        return foi;
    }

    /**
     * @return result[0] := newName<br> result[1] := originaleName
     */
    private String[] createCleanNCName(final Resource res) {
        // implement check for NCName compliance and remove bad values
        String name = res.getName();
        final String origName = name;
        // clean rest of string using Constants.UNICODE_REPLACER
        final char[] foiNameChars = name.toCharArray();
        for (int i = 0; i < foiNameChars.length; i++) {
            final char c = foiNameChars[i];
            if (!NcNameResolver.isNCNameChar(c)) {
                foiNameChars[i] = Configuration.UNICODE_REPLACER;
            }
        }
        name = String.valueOf(foiNameChars);
        // check if name is only containing "_"
        final Matcher matcher = Configuration.UNICODE_ONLY_REPLACER_LEFT_PATTERN.matcher(name);
        if (matcher.matches()) {
            // if yes -> change to "className" + res.getUri().hashCode()
            name = res.getClass().getSimpleName().toLowerCase() + res.getUri().hashCode();
        }
        final String[] result = { name, origName };
        return result;
    }

    /**
     * <p>getValue.</p>
     *
     * @param mVColumn a int.
     * @param values an array of {@link java.lang.String} objects.
     * @return a {@link java.lang.Object} object.
     * @throws java.text.ParseException if any.
     */
    public Object getValue(final int mVColumn, final String[] values) throws ParseException {
        LOG.trace(String.format("getValue(%s,%s)",
                mVColumn,
                Arrays.toString(values)));
        final Column column = configuration.getColumnById(mVColumn);
        String value = values[mVColumn];
        for (final Metadata m : column.getMetadataArray()) {
            if (m.getKey().equals(Key.TYPE)) {
                // check various types of observation
                // TEXT
                if (m.getValue().equals("TEXT")) {
                    return value;
                }
                // text is done -> clean string before parsing to other types
                value = value.trim();
                // BOOLEAN
                if (m.getValue().equals("BOOLEAN")) {
                    if (value.equalsIgnoreCase("0")) {
                        value = "false";
                    } else if (value.equalsIgnoreCase("1")) {
                        value = "true";
                    }
                    return Boolean.parseBoolean(value);
                } else if (m.getValue().equals("COUNT")) {
                    // COUNT
                    return (int) configuration.parseToDouble(value);
                } else if (m.getValue().equals("NUMERIC")) {
                    // NUMERIC
                    return configuration.parseToDouble(value);
                }
            }
        }
        return null;
    }

    /**
     * <p>getTimeStamp.</p>
     *
     * @param mVColumn a int.
     * @param values an array of {@link java.lang.String} objects.
     * @return a {@link org.n52.sos.importer.feeder.model.Timestamp} object.
     * @throws java.text.ParseException if any.
     */
    public Timestamp getTimeStamp(final int mVColumn, final String[] values) throws ParseException {
        LOG.trace("getTimeStamp()");
        // if RelatedDateTimeGroup is set for mvColumn -> get group id
        final Column col = configuration.getColumnById(mVColumn);
        String group = null;
        if (col.isSetRelatedDateTimeGroup()) {
            group = col.getRelatedDateTimeGroup();
        }
        // else check all columns for Type::DATE_TIME -> get Metadata.Key::GROUP->Value
        if (group == null) {
            group = configuration.getFirstDateTimeGroup();
        }
        Column[] cols = configuration.getAllColumnsForGroup(group, Type.DATE_TIME);
        if (cols != null) {
            // Try to get timezone from configuration
            final Timestamp ts = new Timestamp();
            TimeZone timeZone = getTimeZone(cols);
            if (isUnixTime(cols)) {
                handleUnixTime(values, cols, ts);
            } else {
                handleDateTimeCombination(values, cols, ts, timeZone);
            }
            if (configuration.isDateInfoExtractionFromFileNameSetupValid()) {
                ts.enrich(
                        dataFile.getName(),
                        configuration.getRegExDateInfoInFileName(),
                        configuration.getDateInfoPattern());
            }
            if (configuration.isUseDateInfoFromFileModificationSet()) {
                ts.enrich(dataFile.lastModified(), configuration.getLastModifiedDelta());
            }
            return ts;
        }
        return null;
    }

    private void handleDateTimeCombination(final String[] values, final Column[] cols, final Timestamp ts,
            TimeZone timeZone) throws ParseException {
        // TODO implement case if time zone is contained in a column
        // get value from each column
        for (final Column column : cols) {
            // get pattern and fields
            final String pattern = getParsePattern(column);
            final int[] fields = getGregorianCalendarFields(pattern);
            for (final int field : fields) {
                // parse values
                final short value =
                        parseTimestampComponent(values[column.getNumber()],
                                pattern,
                                field,
                                timeZone);
                // add to timestamp object
                switch (field) {
                    case GregorianCalendar.YEAR:
                        ts.setYear(value);
                        break;
                    case GregorianCalendar.MONTH:
                        // java starts month counting at 0 -> +1 for each month
                        ts.setMonth((byte) (value + 1));
                        break;
                    case GregorianCalendar.DAY_OF_MONTH:
                        ts.setDay((byte) value);
                        break;
                    case GregorianCalendar.HOUR_OF_DAY:
                        ts.setHour((byte) value);
                        break;
                    case GregorianCalendar.MINUTE:
                        ts.setMinute((byte) value);
                        break;
                    case GregorianCalendar.SECOND:
                        ts.setSeconds((byte) value);
                        break;
                    case GregorianCalendar.ZONE_OFFSET:
                        ts.setTimezone((byte) value);
                        break;
                    default:
                        break;
                }
            }
            enrichTimestampWithColumnMetadata(ts, column);
        }
    }

    private void handleUnixTime(final String[] values, final Column[] cols, final Timestamp ts) {
        TimeZone timeZone;
        // handle unix time:
        // DO: TZ := UTC; value from one single column (should be an integer)
        timeZone = TimeZone.getTimeZone("UTC");
        ts.setTimezone((byte) (timeZone.getRawOffset() / MILLIES_PER_HOUR));
        ts.set((long) (Double.parseDouble(values[cols[0].getNumber()]) * 1000));
    }

    private boolean isUnixTime(final Column[] cols) {
        // PRE: 1 column and TYPE DATE_TIME and metadata:type:UNIX_TIME
        return cols.length == 1 &&
                containsUnixTimeType(cols[0].getMetadataArray());
    }

    private boolean containsUnixTimeType(Metadata[] metadataArray) {
        if (metadataArray == null || metadataArray.length == 0) {
            return false;
        }
        for (Metadata metadata : metadataArray) {
            if (metadata.getKey().equals(Key.TYPE) && metadata.getValue().equals("UNIX_TIME")) {
                return true;
            }
        }
        return false;
    }

    private TimeZone getTimeZone(final Column[] cols) {
        if (cols == null || cols.length < 1) {
            return TimeZone.getDefault();
        }
        for (final Column column : cols) {
            if (column.getMetadataArray() == null ||
                    column.getMetadataArray().length < 1) {
                continue;
            }
            for (final Metadata meta : column.getMetadataArray()) {
                if (meta.getKey().equals(Key.TIME_ZONE)) {
                    try {
                        for (final String zoneId :
                                TimeZone.getAvailableIDs(Integer.parseInt(meta.getValue()) * MILLIES_PER_HOUR)) {
                            return TimeZone.getTimeZone(zoneId);
                        }
                    } catch (final NumberFormatException nfe) {
                        LOG.error("Could not parse interger from timezone metadata value. Using default timezone");
                        LOG.debug("Exception thrown: ", nfe);
                        return TimeZone.getDefault();
                    }
                }
            }
        }
        return TimeZone.getDefault();
    }

    /**
     * Checks for <code>Column.Metadata[]</code> and updates and returns the given {@link Timestamp}. Allowed keys are:
     * <ul><li>TIME_DAY</li>
     * <li>TIME_HOUR</li>
     * <li>TIME_MINUTE</li>
     * <li>TIME_MONTH</li>
     * <li>TIME_SECOND</li>
     * <li>TIME_YEAR</li>
     * <li>TIME_ZONE</li></ul>
     */
    private void enrichTimestampWithColumnMetadata(final Timestamp ts,
            final Column col) {
        if (col.getMetadataArray() != null) {
            for (final Metadata m : col.getMetadataArray()) {
                if (m.getKey().equals(Key.TIME_ZONE)) {
                    ts.setTimezone(Byte.parseByte(m.getValue()));
                    continue;
                }
                if (m.getKey().equals(Key.TIME_YEAR)) {
                    ts.setYear(Short.parseShort(m.getValue()));
                    continue;
                }
                if (m.getKey().equals(Key.TIME_MONTH)) {
                    ts.setMonth(Byte.parseByte(m.getValue()));
                    continue;
                }
                if (m.getKey().equals(Key.TIME_DAY)) {
                    ts.setDay(Byte.parseByte(m.getValue()));
                    continue;
                }
                if (m.getKey().equals(Key.TIME_HOUR)) {
                    ts.setHour(Byte.parseByte(m.getValue()));
                    continue;
                }
                if (m.getKey().equals(Key.TIME_MINUTE)) {
                    ts.setMinute(Byte.parseByte(m.getValue()));
                    continue;
                }
                if (m.getKey().equals(Key.TIME_SECOND)) {
                    ts.setSeconds(Byte.parseByte(m.getValue()));
                    continue;
                }
            }
        }
    }

    /**
     * Case A.1: RelatedUnitOfMeasurement is a IDref<br>
     *          -&gt; Case A.1.1 or A.1.2<br>
     * <br>
     * Case A.1.1: Related UOM resource is manual<br>
     *          -&gt; get code from name element <br>
     *              UnitOfMeasurement.ManualResource.Name<br>
     * <br>
     * Case A.1.2: Related UOM resource is generated<br>
     *          -&gt; generate name and return its value<br>
     * <br>
     *
     * Case A.2: RelatedUnitOfMeasurement is a number<br>
     *          -&gt; get information from the column<br>
     *          -&gt; return values[number]<br>
     * <br>
     * Case B: RelatedUnitOfMeasurement is not set<br>
     *          -&gt; Check for column with Type == "UOM"<br>
     *          -&gt; get number of this column<br>
     *          -&gt; return values[number]<br>
     *
     * @param mVColumnId a int.
     * @param values an array of {@link java.lang.String} objects.
     * @return a {@link UnitOfMeasurement} object.
     */
    public UnitOfMeasurement getUnitOfMeasurement(final int mVColumnId, final String[] values) {
        LOG.trace("getUnitOfMeasurement()");
        final Column mvColumn = configuration.getColumnById(mVColumnId);

        // Case A*
        if (mvColumn.getRelatedUnitOfMeasurementArray() != null &&
                mvColumn.getRelatedUnitOfMeasurementArray().length > 0) {
            final RelatedUnitOfMeasurement relUom =
                    mvColumn.getRelatedUnitOfMeasurementArray(0);

            // Case A.1.*: idRef
            if (relUom.isSetIdRef() && !relUom.isSetNumber()) {
                final UnitOfMeasurementType uom = configuration.getUomById(relUom.getIdRef());
                if (uom != null) {

                    // Case A.1.1
                    if (uom.getResource() instanceof ManualResourceType) {
                        final ManualResourceType uomMRT =
                                (ManualResourceType) uom.getResource();
                        return new UnitOfMeasurement(uomMRT.getName(), uomMRT.getURI().getStringValue());
                    }

                    // Case A.1.2
                    if (uom.getResource() instanceof GeneratedResourceType) {
                        final GeneratedResourceType uomGRT =
                                (GeneratedResourceType) uom.getResource();
                        final String[] a = getUriAndNameFromGeneratedResourceType(
                                uomGRT.isSetConcatString()
                                ? uomGRT.getConcatString()
                                        : "",
                                "",
                                false,
                                uomGRT.getNumberArray(),
                                values);
                        return new UnitOfMeasurement(a[0], a[1]);
                    }
                }
            }

            // Case A.2: number
            if (relUom.isSetNumber() && !relUom.isSetIdRef()) {
                return new UnitOfMeasurement(values[relUom.getNumber()], values[relUom.getNumber()]);
            }
        }

        // Case B: Information stored in another column
        final int uomColumnId = configuration.getColumnIdForUom(mVColumnId);
        if (uomColumnId > -1) {
            return new UnitOfMeasurement(values[uomColumnId], values[uomColumnId]);
        }

        // no UOM found
        return null;
    }

    /**
     * Case A.1: RelatedObserverdProperty is a IDref<br>
     *          -&gt; Case A.1.1 or A.1.2<br>
     * <br>
     * Case A.1.1: RelatedObserverdProperty resource is manual<br>
     *          -&gt; get code from name element <br>
     *              ObserverdProperty.ManualResource.Name<br>
     * <br>
     * Case A.1.2: RelatedObserverdProperty resource is generated<br>
     *          -&gt; generate name and return its value<br>
     * <br>
     *
     * Case A.2: RelatedObserverdProperty is a number<br>
     *          -&gt; get information from the column<br>
     *          -&gt; return values[number]<br>
     * <br>
     * Case B: RelatedObserverdProperty is not set<br>
     *          -&gt; Check for column with Type == "UOM"<br>
     *          -&gt; get number of this column<br>
     *          -&gt; return values[number]<br>
     *
     * @param mVColumnId a int.
     * @param values an array of {@link java.lang.String} objects.
     * @return a {@link org.n52.sos.importer.feeder.model.ObservedProperty} object.
     */
    public ObservedProperty getObservedProperty(final int mVColumnId, final String[] values) {
        LOG.trace("getObservedProperty()");
        final Column mvColumn = configuration.getColumnById(mVColumnId);

        // Case A*
        if (mvColumn.getRelatedObservedPropertyArray() != null &&
                mvColumn.getRelatedObservedPropertyArray().length > 0) {
            final RelatedObservedProperty relOp =
                    mvColumn.getRelatedObservedPropertyArray(0);

            // Case A.1.*: idRef
            if (relOp.isSetIdRef() && !relOp.isSetNumber()) {
                final ObservedPropertyType op = configuration.getObsPropById(relOp.getIdRef());
                if (op != null) {

                    // Case A.1.1
                    if (op.getResource() instanceof ManualResourceType) {
                        final ManualResourceType opMRT =
                                (ManualResourceType) op.getResource();
                        return new ObservedProperty(opMRT.getName(), opMRT.getURI().getStringValue());

                    }

                    // Case A.1.2
                    if (op.getResource() instanceof GeneratedResourceType) {
                        final GeneratedResourceType opGRT =
                                (GeneratedResourceType) op.getResource();
                        final String[] a = getUriAndNameFromGeneratedResourceType(
                                opGRT.isSetConcatString()
                                ? opGRT.getConcatString()
                                        : "",
                                opGRT.getURI().getStringValue(),
                                opGRT.getURI().isSetUseAsPrefix()
                                ? opGRT.getURI().getUseAsPrefix()
                                        : false,
                                opGRT.getNumberArray(),
                                values);
                        return new ObservedProperty(a[0], a[1]);
                    }
                }
            }

            // Case A.2: number
            if (relOp.isSetNumber() && !relOp.isSetIdRef()) {
                return new ObservedProperty(values[relOp.getNumber()], values[relOp.getNumber()]);
            }
        }

        // Case B: Information stored in another column
        final int opColumnId = configuration.getColumnIdForOpsProp(mVColumnId);
        if (opColumnId > -1) {
            return new ObservedProperty(values[opColumnId], values[opColumnId]);
        }

        // no OP found
        return null;
    }

    /**
     * <p>getOffering.</p>
     *
     * @param s a {@link Sensor} object.
     * @return a {@link Offering} object.
     */
    public Offering getOffering(final Sensor s) {
        final Offering off = configuration.getOffering(s);
        if (!NcNameResolver.isNCName(off.getName())) {
            final String[] a = createCleanNCName(off);
            off.setName(a[0]);
            if (!a[0].equals(a[1])) {
                LOG.debug(String.format("Offering name changed to match NCName production: '%s' to '%s'",
                        a[1],
                        a[0]));
            }
        }
        return off;
    }

    /**
     * <p>getFileName.</p>
     *
     * @return the name of the data dataFile. Not the whole path.
     */
    public String getFileName() {
        return dataFile.getName();
    }

    /**
     * <p>getCanonicalPath.</p>
     *
     * @return a {@link String} object.
     * @throws IOException if any.
     */
    public String getCanonicalPath() throws IOException {
        return dataFile.getCanonicalPath();
    }

    /**
     * <p>getConfigurationFileName.</p>
     *
     * @return a {@link String} object.
     */
    public String getConfigurationFileName() {
        return configuration.getFileName();
    }

    /**
     * <code>String[] result = {name,uri};</code>
     * @return <code>String[] result = {name,uri};</code>
     */
    private String[] getUriAndNameFromGeneratedResourceType(
            final String concatString,
            final String uri,
            final boolean useUriAsPrefixAfterNameAsUri,
            final int[] columnIds,
            final String[] values) {
        LOG.trace(String.format("getValuesFromResourceType(%s,%s,%b,%s,%s)",
                concatString,
                uri,
                useUriAsPrefixAfterNameAsUri,
                Arrays.toString(columnIds),
                Arrays.toString(values)));
        String name = "";
        // first the name
        String glue = concatString;
        if (concatString == null) {
            glue = "";
        }
        for (int i = 0; i < columnIds.length; i++) {
            if (i > 0) {
                name = name + glue + values[columnIds[i]];
            } else {
                name = values[columnIds[i]];
            }
            LOG.trace(String.format(NAME, name));
        }
        LOG.debug(String.format(NAME, name));
        // than the uri
        String myUri = name;
        if (uri != null && useUriAsPrefixAfterNameAsUri) {
            myUri = uri + name;
        }
        LOG.debug(String.format("uri: %s", uri));
        final String[] result = {name, myUri};
        return result;
    }

    private Sensor getSensorFromColumn(final int mvColumnId, final String[] values) {
        LOG.trace(String.format("getSensorColumn(%d,%s)",
                mvColumnId,
                Arrays.toString(values)));
        final int i = configuration.getColumnIdForSensor(mvColumnId);
        if (i < 0) {
            // sensor is not in the data dataFile -> return null
            return null;
        } else {
            final Sensor s = new Sensor(values[i], values[i]);
            LOG.debug(String.format("Sensor found in datafile: %s", s));
            return s;
        }
    }

    private FeatureOfInterest getFoiColumn(final int mvColumnId, final String[] values) throws ParseException {
        LOG.trace(String.format("getFoiColumn(%d,...)",
                    mvColumnId));
        final int i = configuration.getColumnIdForFoi(mvColumnId);
        if (i < 0) {
            // foi is not in the data dataFile -> return null
            return null;
        } else {
            Position p = configuration.getFoiPosition(values[i]);
            if (p == null) {
                p = configuration.getPosition(values);
            } else {
                LOG.error(String.format("Could not find position for foi '%s'", values[i]));
            }
            final FeatureOfInterest s = new FeatureOfInterest(values[i],
                    values[i],
                    p);
            LOG.debug(String.format("Feature of Interst found in datafile: %s", s));
            return s;
        }
    }

    private Position getPosition(
            final org.x52North.sensorweb.sos.importer.x04.PositionDocument.Position p,
            final String[] values) throws ParseException {
        LOG.trace(String.format("getPosition(%s,%s)",
                    p.xmlText(),
                    Arrays.toString(values)));
        // Case A: Position is in configuration
        if (!p.isSetGroup() &&
                //p.isSetAlt() &&
                p.isSetEPSGCode() &&
                p.isSetLat() &&
                p.isSetLong()) {
            return configuration.getModelPositionXBPosition(p);
        } else if (p.isSetGroup() &&
                //!p.isSetAlt() &&
                !p.isSetEPSGCode() &&
                !p.isSetLat() &&
                !p.isSetLong()) {
            // Case B: Position is in data dataFile (and configuration [missing values])
            return configuration.getPosition(p.getGroup(), values);
        }
        return null;
    }

    private short parseTimestampComponent(
            final String timestampPart,
            final String pattern,
            final int field,
            final TimeZone timeZone)
                    throws ParseException {
        LOG.trace(String.format("parseTimestampComponent(%s,%s,%d)",
                    timestampPart,
                    pattern,
                    field));
        Date date = null;
        final SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(timeZone);

        String parsebleTimestamp = timestampPart;
        if (timestampPart.indexOf('Z') != -1) {
            parsebleTimestamp = timestampPart.replaceAll("Z", "+0100");
        }

        date = sdf.parse(parsebleTimestamp);

        final GregorianCalendar gc = new GregorianCalendar(timeZone);
        gc.setTime(date);

        return (short) gc.get(field);
    }

    private int[] getGregorianCalendarFields(final String pattern) {
        LOG.trace(String.format("getGregorianCalendarFields(%s)",
                    pattern));
        final ArrayList<Integer> fields = new ArrayList<Integer>();
        if (pattern.indexOf("y") != -1) {
            fields.add(GregorianCalendar.YEAR);
        }
        if (pattern.indexOf("M") != -1 ||
                pattern.indexOf("w") != -1 ||
                pattern.indexOf("D") != -1) {
            fields.add(GregorianCalendar.MONTH);
        }
        if (pattern.indexOf("d") != -1 ||
                (pattern.indexOf("W") != -1 && pattern.indexOf("d") != -1)) {
            fields.add(GregorianCalendar.DAY_OF_MONTH);
        }
        if (pattern.indexOf("H") != -1 ||
                pattern.indexOf("k") != -1 ||
                ((pattern.indexOf("K") != -1 ||
                (pattern.indexOf("h") != -1) && pattern.indexOf("a") != -1))) {
            fields.add(GregorianCalendar.HOUR_OF_DAY);
        }
        if (pattern.indexOf("m") != -1) {
            fields.add(GregorianCalendar.MINUTE);
        }
        if (pattern.indexOf("s") != -1) {
            fields.add(GregorianCalendar.SECOND);
        }
        if (pattern.indexOf("Z") != -1 || pattern.indexOf("z") != -1) {
            fields.add(GregorianCalendar.ZONE_OFFSET);
        }
        fields.trimToSize();
        final int[] result = new int[fields.size()];
        int j = 0;
        for (final Integer i : fields) {
            result[j++] = i.intValue();
        }
        return result;
    }

    private String getParsePattern(final Column column) {
        LOG.trace("getParsePattern()");
        if (column.getMetadataArray() != null && column.getMetadataArray().length > 1) {
            for (final Metadata m : column.getMetadataArray()) {
                if (m.getKey().equals(Key.PARSE_PATTERN)) {
                    LOG.debug(String.format("Parsepattern found: %s",
                                m.getValue()));
                    return m.getValue();
                }
            }
        }
        LOG.debug(String.format("No Metadata element found with key %s in column %s",
                    Key.PARSE_PATTERN.toString(),
                    column.xmlText()));
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("DataFile [dataFile=%s, configuration=%s]", dataFile, configuration);
    }

    /**
     * <p>getType.</p>
     *
     * @param mVColumnId a int.
     * @return a {@link java.lang.String} object.
     */
    public String getType(final int mVColumnId) {
        return configuration.getType(mVColumnId);
    }

    /**
     * <p>getExpectedColumnCount.</p>
     *
     * @return a int.
     */
    public int getExpectedColumnCount() {
        return configuration.getExpectedColumnCount();
    }

    /**
     * <p>getHeaderLine.</p>
     *
     * @return a int.
     */
    public int getHeaderLine() {
        return configuration.getHeaderLine();
    }

    /**
     * <p>getEncoding.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getEncoding() {
        return configuration.getDataFileEncoding();
    }

    /**
     * <p>getSeparatorChar.</p>
     *
     * @return a char.
     */
    public char getSeparatorChar() {
        return configuration.getCsvSeparator();
    }
}
