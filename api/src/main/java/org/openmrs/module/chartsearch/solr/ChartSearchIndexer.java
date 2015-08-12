/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chartsearch.solr;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.openmrs.module.chartsearch.apiIndexing.ChartSearchAPIIndexer;
import org.openmrs.module.chartsearch.server.ConfigCommands;
import org.openmrs.module.chartsearch.server.PatientInfo;
import org.openmrs.module.chartsearch.server.StatisticsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChartSearchIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(ChartSearchIndexer.class);
	
	public ChartSearchIndexer() {
	}
	
	public void indexPatientData(Integer personId) {
		SolrServer solrServer = SolrSingleton.getInstance().getServer();
		ModifiableSolrParams params = new ModifiableSolrParams();
		//TODO take path from config
		params.set("qt", "/csdataimport");
		params.set("command", "import");
		params.set("clean", false);
		params.set("personId", personId);
		try {
			if (solrServer != null) {
				ChartSearchAPIIndexer apiIndexer = new ChartSearchAPIIndexer();
				
				solrServer.query(params);
				apiIndexer.indexBothPatientAllergiesAndAppointments(personId, solrServer);
			}
		}
		catch (SolrServerException ex) {
			log.error(String.format("Tried to import patient #%d but failed", personId), ex);
		}
	}
	
	/* 
	 * @return null if something going wrong
	 */
	public PatientInfo getPatientInfo(Integer patientId) {
		SolrServer solrServer = SolrSingleton.getInstance().getServer();
		ModifiableSolrParams params = new ModifiableSolrParams();
		//TODO take path from config
		params.set("qt", "/csdataimport");
		params.set("command", ConfigCommands.PATIENT_STATE);
		params.set("personId", patientId);
		
		try {
			QueryResponse response = solrServer.query(params);
			Date lastIndexTime = (Date) response.getResponse().get(ConfigCommands.Labels.PATIENT_LAST_INDEX_TIME);
			if (lastIndexTime == null)
				return null;
			PatientInfo info = new PatientInfo(patientId, lastIndexTime);
			return info;
		}
		catch (SolrServerException ex) {
			log.error(String.format("Failed to get patient state for #%d", patientId), ex);
			return null;
		}
	}
	
	/* 
	 * @return null if something going wrong
	 */
	@SuppressWarnings("unchecked")
	public StatisticsInfo getStatistics() {
		SolrServer solrServer = SolrSingleton.getInstance().getServer();
		ModifiableSolrParams params = new ModifiableSolrParams();
		//TODO take path from config
		params.set("qt", "/csdataimport");
		params.set("command", ConfigCommands.STATS);
		
		try {
			QueryResponse response = solrServer.query(params);
			NamedList<Object> responseList = response.getResponse();
			String clearStrategy = (String) responseList.get(ConfigCommands.Labels.CLEAR_STRATEGY);
			int pruneCount = (Integer) responseList.get(ConfigCommands.Labels.CLEARED_PATIENTS_COUNT);
			List<HashMap<String, Object>> daemonStates = (ArrayList<HashMap<String, Object>>) responseList
			        .get(ConfigCommands.Labels.DAEMON_STATES);
			StatisticsInfo stats = new StatisticsInfo(clearStrategy, pruneCount, daemonStates);
			return stats;
		}
		catch (SolrServerException ex) {
			log.error("Failed to get stats", ex);
			return null;
		}
	}
	
	/* 
	 * TODO refactor, violates  CQS (Command Query Separation)
	 * @return null if something going wrong
	 */
	public Integer clearIndex(String strategy, String ids, Integer maxPatients, Integer ago) {
		SolrServer solrServer = SolrSingleton.getInstance().getServer();
		ModifiableSolrParams params = new ModifiableSolrParams();
		//TODO take path from config
		params.set("qt", "/csdataimport");
		params.set("command", ConfigCommands.PRUNE);
		params.set(ConfigCommands.PRUNE_CLEAR_STRATEGY, strategy);
		if (!StringUtils.isBlank(ids))
			params.set(ConfigCommands.PRUNE_IDS, ids);
		if (maxPatients != null)
			params.set(ConfigCommands.PRUNE_MAX_PATIENTS, maxPatients);
		if (ago != null)
			params.set(ConfigCommands.PRUNE_AGO, ago);
		
		try {
			QueryResponse response = solrServer.query(params);
			NamedList<Object> responseList = response.getResponse();
			
			//TODO somehow return an error message
			if (responseList.get(ConfigCommands.Labels.ERROR) != null)
				return null;
			
			Integer pruneCount = (Integer) response.getResponse().get(ConfigCommands.Labels.CLEARED_PATIENTS_COUNT);
			return pruneCount;
		}
		catch (SolrServerException ex) {
			log.error(String.format("Failed to prune patients\nStrategy: %s\nPatient ids: %s\nMax Patients: %d\nAgo: %d",
			    strategy, ids, maxPatients, ago), ex);
			return null;
		}
	}
	
	/*
	 * TODO refactor, violates  CQS (Command Query Separation)
	 * do not return string
	 */
	public int changeDaemonsCount(int count) {
		SolrServer solrServer = SolrSingleton.getInstance().getServer();
		ModifiableSolrParams params = new ModifiableSolrParams();
		//TODO take path from config
		params.set("qt", "/csdataimport");
		params.set("command", ConfigCommands.SHANGE_DAEMONS_COUNT);
		params.set(ConfigCommands.DAEMONS_COUNT, count);
		
		try {
			QueryResponse response = solrServer.query(params);
			int daemonsCount = (Integer) response.getResponse().get("daemonsCount");
			return daemonsCount;
		}
		catch (SolrServerException ex) {
			log.error("Failed to change daemons count.");
			
			//TODO do not use exception code!
			return -1;
		}
	}
	
}
