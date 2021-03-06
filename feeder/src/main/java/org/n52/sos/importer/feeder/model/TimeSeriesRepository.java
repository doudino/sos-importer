/**
 * Copyright (C) 2011-2015 52°North Initiative for Geospatial Open Source
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
package org.n52.sos.importer.feeder.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.n52.sos.importer.feeder.model.requests.InsertObservation;
import org.n52.sos.importer.feeder.model.requests.RegisterSensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeSeriesRepository {

	private static final Logger LOG = LoggerFactory.getLogger(TimeSeriesRepository.class);

	private final Vector<TimeSeries> repo;

	public TimeSeriesRepository(final int numberOfTimeSeries) {
		repo = new Vector<TimeSeries>(numberOfTimeSeries);
		for (int i = 0; i < numberOfTimeSeries; i++) {
			repo.add(i, new TimeSeries());
		}
	}

	/**
	 * @param ios the {@link InsertObservation}s to add using the array index as timeseries id
	 */
	public void addObservations(final InsertObservation[] ios) {
		if (ios.length != repo.capacity()) {
			throw new IllegalArgumentException("Number of InsertObservations is not matching number of timeseries!");
		}
		if (ios != null) {
			for (int i = 0; i < ios.length; i++) {
				repo.get(i).addObservation(ios[i]);
			}
		}
	}

	public Collection<TimeSeries> getTimeSeries() {
		return repo;
	}

	/**
	 * @param sensorURI the URI of the Sensor
	 * 
	 * @return A {@link RegisterSensor} using
	 * 			<ul>
	 * 				<li>ObservedProperty</li>
	 * 				<li>MeasureValueType</li>
	 * 				<li>UnitOfMeasurement</li>
	 * 			</ul>
	 * 			from all timeseries in this {@link TimeSeriesRepository} 
	 * 			having the Sensor with given URI <code>sensorURI</code>.
	 * 
	 * @throws IllegalArgumentException if no timeseries for the given 
	 * 			<code>sensorURI</code> are available in this repository.
	 */
	public RegisterSensor getRegisterSensor(final String sensorURI) {
		// get all time series of sensor
		List<TimeSeries> timeseries = new ArrayList<>(repo.capacity());
		for (TimeSeries series : getTimeSeries()) {
			if (series.getSensorURI().equals(sensorURI)) {
				timeseries.add(series);
			}
		}
		if (timeseries.isEmpty()) {
			throw new IllegalArgumentException("Could not find any time series for sensor '" + sensorURI + "'");
		}
		final InsertObservation io = timeseries.get(0).getFirst();
		
		return new RegisterSensor(io,
				getObservedProperties(timeseries),
				getMeasuredValueTypes(timeseries),
				getUnitsOfMeasurement(timeseries));
	}

	private Map<ObservedProperty, String> getUnitsOfMeasurement(List<TimeSeries> timeseries) {
		LOG.trace("getUnitsOfMeasurement(...)");
		final Map<ObservedProperty,String> unitsOfMeasurement = new HashMap<ObservedProperty, String>(timeseries.size());
		for (final TimeSeries ts : timeseries) {
			unitsOfMeasurement.put(ts.getObservedProperty(), ts.getUnitOfMeasurementCode());
		}
		return unitsOfMeasurement;
	}

	private Map<ObservedProperty, String> getMeasuredValueTypes(List<TimeSeries> timeseries) {
		LOG.trace("getMeasuredValueTypes(...)");
		final Map<ObservedProperty,String> measuredValueTypes = new HashMap<ObservedProperty, String>(timeseries.size());
		for (final TimeSeries ts : timeseries) {
			measuredValueTypes.put(ts.getObservedProperty(), ts.getMeasuredValueType());
		}
		return measuredValueTypes;
	}

	private Collection<ObservedProperty> getObservedProperties(List<TimeSeries> timeseries) {
		LOG.trace("getObservedProperties(...)");
		final Set<ObservedProperty> observedProperties = new HashSet<ObservedProperty>(timeseries.size());
		for (final TimeSeries ts : timeseries) {
				observedProperties.add(ts.getObservedProperty());
		}
		return observedProperties;
	}

	/**
	 * @return <code>true</code>, in the case of containing only empty time series
	 */
	public boolean isEmpty() {
		for (final TimeSeries timeSeries : getTimeSeries()) {
			if (!timeSeries.isEmpty()) {
				return false;
			}
		}
		return true;
	}

}
