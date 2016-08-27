package org.sagebionetworks.repo.model.dbo.throttle;

import static org.junit.Assert.*;

import java.sql.Date;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.apache.logging.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.repo.model.dao.throttle.ThrottleRulesDAO;
import org.sagebionetworks.repo.model.throttle.ThrottleRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:jdomodels-test-context.xml" })
public class DBOThrottleRulesImplTest {
	
	@Autowired
	ThrottleRulesDAO throttleRulesDao;
	
	private static String testUri = "/fake/uri/#/asdf/";
	private static long maxCalls = 3;
	private static long callPeriod = 30;
	
	@Before
	public void setUp() throws Exception {
		assertNotNull(throttleRulesDao);
		throttleRulesDao.clearAllThrottles();
		
	}

	@After
	public void tearDown() throws Exception {
		assertNotNull(throttleRulesDao);
		throttleRulesDao.clearAllThrottles();
	}
	
	///////////////////////
	// addThrottle() Tests
	///////////////////////

	@Test (expected = IllegalArgumentException.class)
	public void testAddThrottleNegativeId() {
		throttleRulesDao.addThrottle(-1, testUri, maxCalls, callPeriod);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testAddThrottleNullUri() {
		throttleRulesDao.addThrottle(1, null, maxCalls, callPeriod);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testAddThrottleNegativeMaxCalls() {
		throttleRulesDao.addThrottle(1, testUri, -1, callPeriod);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testAddThrottleNegativeCallPeriod() {
		throttleRulesDao.addThrottle(1, testUri, 1, -1);
	}
	
	@Ignore
	@Test (expected = DuplicateKeyException.class)
	public void testAddThrottleDuplicateUri(){
		throttleRulesDao.addThrottle(1, testUri, maxCalls, callPeriod);
		throttleRulesDao.addThrottle(2, testUri, maxCalls, callPeriod);
	}
	
	@Test (expected = DuplicateKeyException.class)
	public void testAddThrottleDuplicateId(){
		throttleRulesDao.addThrottle(1, testUri, maxCalls, callPeriod);
		throttleRulesDao.addThrottle(1, testUri + "asdf", maxCalls, callPeriod);
	}
	
	public void testAddThrottle(){
		long id = 0;
		
		throttleRulesDao.addThrottle(id, testUri, maxCalls, callPeriod);
		
		List<ThrottleRule> throttles = throttleRulesDao.getAllThrottles();
		assertEquals(1, throttles.size());
		ThrottleRule throttleRule = throttles.get(0);
		assertEquals((Long) id, throttleRule.getId());
		assertEquals(testUri, throttleRule.getNormalizedUri());
		assertEquals((Long) maxCalls, throttleRule.getMaxCalls());
		assertEquals((Long) callPeriod, throttleRule.getCallPeriodSec());
	}
	
	
	/////////////////////////
	// getAllThrottles Tests
	/////////////////////////
	@Test
	public void testGetAllThrottles(){
		//should be initially empty
		List<ThrottleRule> throttles= throttleRulesDao.getAllThrottles();
		assertEquals(0, throttles.size());
		
		// add 2 throttles
		throttleRulesDao.addThrottle(0, testUri, maxCalls, callPeriod);
		throttleRulesDao.addThrottle(1, testUri + "asdf", maxCalls, callPeriod);
		
		throttles = throttleRulesDao.getAllThrottles();
		assertEquals(2, throttles.size());
	}
	
	////////////////////////////////
	// getAllThrottlesAfter() Tests
	////////////////////////////////
	
	@Test (expected = IllegalArgumentException.class)
	public void testGetAllThrottlesAfterNullDate(){
		throttleRulesDao.getAllThrottlesAfter(null);
	}
	@Ignore // this test is also failing on hudson
	@Test
	public void testGetAllThrottlesAfter(){
		throttleRulesDao.addThrottle(1, testUri, maxCalls, callPeriod);
		try {
			Thread.sleep(1000); //sleep for 1 seconds since mysql timestamps are only accurate to seconds
			Date timeAfterSleep = new Date(System.currentTimeMillis());
			throttleRulesDao.addThrottle(2, testUri + "asdf", maxCalls, callPeriod);
			List<ThrottleRule> throttles= throttleRulesDao.getAllThrottlesAfter(timeAfterSleep);
			assertEquals(1, throttles.size());
			assertEquals( (Long) 2L, throttles.get(0).getId());
		} catch (InterruptedException e) {
			LogManager.getLogger(DBOThrottleRulesImplTest.class).catching(e);
		}
	}
	
}