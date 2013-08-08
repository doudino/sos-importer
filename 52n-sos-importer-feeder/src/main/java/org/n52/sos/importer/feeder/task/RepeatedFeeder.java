/**
 * Copyright (C) 2012
 * by 52North Initiative for Geospatial Open Source Software GmbH
 *
 * Contact: Andreas Wytzisk
 * 52 North Initiative for Geospatial Open Source Software GmbH
 * Martin-Luther-King-Weg 24
 * 48155 Muenster, Germany
 * info@52north.org
 *
 * This program is free software; you can redistribute and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 *
 * This program is distributed WITHOUT ANY WARRANTY; even without the implied
 * WARRANTY OF MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program (see gnu-gpl v2.txt). If not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA or
 * visit the Free Software Foundation web page, http://www.fsf.org.
 */
package org.n52.sos.importer.feeder.task;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.n52.sos.importer.feeder.Configuration;
import org.n52.sos.importer.feeder.FileHelper;
import org.n52.sos.importer.feeder.InvalidColumnCountException;
import org.n52.sos.importer.feeder.JavaApiBugJDL6203387Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:e.h.juerrens@52north.org">Eike Hinderk J&uuml;rrens</a>
 *
 */
public class RepeatedFeeder extends TimerTask{
	
	private static final Logger LOG = LoggerFactory.getLogger(RepeatedFeeder.class);

	private final Configuration configuration;
	private final File file;
	
	final private static Lock oneFeederLock = new ReentrantLock(true);
	
	private static File lastUsedDateFile;
	
	public RepeatedFeeder(final Configuration c) {
		this(c,c.getDataFile());
	}

	public RepeatedFeeder(final Configuration c, final File f) {
		configuration = c;
		file = f;
	}

	@Override
	public void run() {
		File datafile;
		oneFeederLock.lock(); // used to sync access to lastUsedDateFile and to not have more than one feeder at a time.
		try {
			// FIXME implement new logic
			/*
			 * save last feeded file incl. counter
			 * check for newer files
			 * each on own thread?
			 * 	feed all obs in last feeded file
			 * 	feed all newer files
			 */
			// if file is a directory, get latest from file list
			if (file.isDirectory()) {
				final ArrayList<File> filesToFeed = new ArrayList<File>();
				getLastFeedFile();
				if (lastUsedDateFile != null) {
					filesToFeed.add(lastUsedDateFile);
				}
				addNewerFiles(filesToFeed);
				// TODO for each in "filesToFeed" run a single OneTimeFeeder
				for (final File fileToFeed : filesToFeed) {
					LOG.info("Start feeding file {}",fileToFeed.getName());
					try {
						new OneTimeFeeder(configuration, fileToFeed).run();
						LOG.info("Finished feeding file {}.",fileToFeed.getName());
						lastUsedDateFile = fileToFeed;
						saveLastFeedFile();
					} 
					catch (final InvalidColumnCountException iae) {
						// Exception is already logged -> nothing to do
					}
					catch (final JavaApiBugJDL6203387Exception e) {
						// Exception is already logged -> nothing to do
					}
				}
			} else {
				datafile = file;
				// OneTimeFeeder with file override used not as thread
				new OneTimeFeeder(configuration, datafile).run();
			}
			// TODO finished feeding for datafile -> store as last feeded
		} catch (final Exception e) {
			LOG.debug("Exception catched: {}", e.getMessage(),e);
		} finally {
			oneFeederLock.unlock();
		}
	}

	private void addNewerFiles(final ArrayList<File> filesToFeed)
	{
		// TODO if last feed file is null: add all (OR only the newest?) files in directory to list "filesToFeed"
		// TODO else: get all files newer than last feed file and add to list "filesToFeed"
		final File[] files = file.listFiles(new FileFilter() {
			@Override
			public boolean accept(final File pathname) {
				return pathname.isFile() &&
						pathname.canRead() &&
						(configuration.getLocaleFilePattern() != null?
						configuration.getLocaleFilePattern().matcher(pathname.getName()).matches():true);
			}
		});
		if (files != null) {

			for (final File file2 : files) {
				if (lastUsedDateFile == null || file2.lastModified() > lastUsedDateFile.lastModified()) {
					filesToFeed.add(file2);
				}
			}
			if (filesToFeed.size() < 1) {
				LOG.error("No new file found in directory '{}'. Last used file was '{}'.",
						file.getAbsolutePath(),
						lastUsedDateFile.getName());
			}
		} else {
			LOG.error("No file found in directory '{}'",file.getAbsolutePath());
		}
	}
	
	private void saveLastFeedFile()
	{
		// TODO Auto-generated method "saveLastFeedFile" stub generated on 07.08.2013 around 10:35:50 by eike
		final Properties prop = new Properties();
		prop.put("lastFeedFile", lastUsedDateFile.getAbsolutePath());
		try {
			prop.store(new FileWriter(FileHelper.getHome().getAbsolutePath() + File.separator + FileHelper.cleanPathToCreateFileName(configuration.getConfigFile().getAbsolutePath() + "_lastFeedFile")), null);
			LOG.info("Saved last used data file: {}", lastUsedDateFile.getName());
		} catch (final IOException e) {
			LOG.error("Exception thrown: {}", e.getMessage(), e);
		}
	}

	private void getLastFeedFile()
	{
		// TODO get last feed file from .SOSImporter/$configfile_lastFeedFile and add to list "filesToFeed"
		final Properties prop = new Properties();
		try {
			prop.load(new FileReader(FileHelper.getHome().getAbsolutePath() + File.separator + FileHelper.cleanPathToCreateFileName(configuration.getConfigFile().getAbsolutePath() + "_lastFeedFile")));
		} catch (final IOException e) {
			LOG.debug("Exception thrown: {}", e.getMessage(), e); // only on DEBUG because it is not a problem if this file does not exist
		}
		final String lastFeedFileName = prop.getProperty("lastFeedFile");
		if (lastFeedFileName == null) {
			return;
		}
		final File lastFeedFile = new File(lastFeedFileName);
		if (lastFeedFile.canRead()) {
			lastUsedDateFile = lastFeedFile;
		} else {
			lastUsedDateFile = null;
		}
	}

}
