package org.sagebionetworks.repo;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.mockito.MockitoAnnotations;
import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.audit.utils.VirtualMachineIdProvider;
import org.sagebionetworks.cloudwatch.Consumer;
import org.sagebionetworks.cloudwatch.ProfileData;
import org.sagebionetworks.util.Clock;
import org.springframework.test.util.ReflectionTestUtils;

import com.amazonaws.services.cloudwatch.model.StandardUnit;

public class MemoryLoggerTest {

	@Mock
	Consumer consumer;
	@Mock
	Clock clock;
	@Mock
	StackConfiguration stackConfig;
	
	MemoryLogger memoryLogger;
	
	@Before
	public void before(){
		MockitoAnnotations.initMocks(this);
		memoryLogger = new MemoryLogger();
		ReflectionTestUtils.setField(memoryLogger, "consumer", consumer);
		ReflectionTestUtils.setField(memoryLogger, "clock", clock);
		ReflectionTestUtils.setField(memoryLogger, "stackConfig", stackConfig);
		
		when(clock.currentTimeMillis()).thenReturn(1L, 2L,3L,4L,5L);
		when(stackConfig.isProductionStack()).thenReturn(true);
	}
	
	@Test
	public void testTimerFiredProduction(){
		// setup the time to trigger a publish to cloud watch
		when(clock.currentTimeMillis()).thenReturn(MemoryLogger.PUBLISH_PERIOD_MS+1);
		// setup a production
		when(stackConfig.isProductionStack()).thenReturn(true);
		// call under test
		memoryLogger.onTimerFired();
		ArgumentCaptor<ProfileData> profileCapture = ArgumentCaptor.forClass(ProfileData.class);
		// Since this is production a metric for all and the instance should be published
		verify(consumer, times(2)).addProfileData(profileCapture.capture());
		List<ProfileData> results = profileCapture.getAllValues();
		assertEquals(2, results.size());
		// the first metric should be all
		ProfileData all = results.get(0);
		assertEquals(MemoryLogger.REPO_MEMORY_NAMESPACE, all.getNamespace());
		assertEquals(MemoryLogger.USED, all.getName());
		assertEquals(Collections.singletonMap(MemoryLogger.INSTANCE, MemoryLogger.ALL_INSTANCES), all.getDimension());
		assertEquals(StandardUnit.Bytes.name(), all.getUnit());
		assertNotNull(all.getMetricStats());
		assertEquals(new Double(1), all.getMetricStats().getCount());
		// The min, max, and sum should all have the same value for first call
		Double maxValue = all.getMetricStats().getMaximum();
		assertNotNull(maxValue);
		assertEquals(maxValue, all.getMetricStats().getMinimum());
		assertEquals(maxValue, all.getMetricStats().getSum());
		
		// the next metric should be the instances
		ProfileData instance = results.get(1);
		assertEquals(MemoryLogger.REPO_MEMORY_NAMESPACE, instance.getNamespace());
		assertEquals(MemoryLogger.USED, instance.getName());
		// VMID used for the instances
		assertEquals(Collections.singletonMap(MemoryLogger.INSTANCE, VirtualMachineIdProvider.getVMID()), instance.getDimension());
		assertNotNull(instance.getMetricStats());
		assertEquals(new Double(1), instance.getMetricStats().getCount());
		// The min, max, and sum should all have the same value for first call
		maxValue = instance.getMetricStats().getMaximum();
		assertNotNull(maxValue);
		assertEquals(maxValue, instance.getMetricStats().getMinimum());
		assertEquals(maxValue, instance.getMetricStats().getSum());
	}
	
	@Test
	public void testTimerFiredNonProduction(){
		// setup the time to trigger a publish to cloud watch
		when(clock.currentTimeMillis()).thenReturn(MemoryLogger.PUBLISH_PERIOD_MS+1);
		// setup a dev
		when(stackConfig.isProductionStack()).thenReturn(false);
		// call under test
		memoryLogger.onTimerFired();
		ArgumentCaptor<ProfileData> profileCapture = ArgumentCaptor.forClass(ProfileData.class);
		// Since this is not production only the all metric should be published.
		verify(consumer, times(1)).addProfileData(profileCapture.capture());
		List<ProfileData> results = profileCapture.getAllValues();
		assertEquals(1, results.size());
		// the first metric should be all
		ProfileData all = results.get(0);
		assertEquals(MemoryLogger.REPO_MEMORY_NAMESPACE, all.getNamespace());
		assertEquals(MemoryLogger.USED, all.getName());
		assertEquals(Collections.singletonMap(MemoryLogger.INSTANCE, MemoryLogger.ALL_INSTANCES), all.getDimension());
	}
	
	@Test
	public void testTimerFiredPeriod(){
		long startTime = MemoryLogger.PUBLISH_PERIOD_MS;
		when(clock.currentTimeMillis()).thenReturn(startTime, startTime+1, startTime+2, startTime+3);
		validateStatsReset();
		// The first time the timer is fired should be under the period
		memoryLogger.onTimerFired();
		verify(consumer, never()).addProfileData(any(ProfileData.class));
		reset(consumer);
		// fire again should trigger a publish
		memoryLogger.onTimerFired();
		validateStatsReset();
		verify(consumer, times(2)).addProfileData(any(ProfileData.class));
		reset(consumer);
		// After publish it should not publish again
		memoryLogger.onTimerFired();
		assertEquals(1L, memoryLogger.getCount());
		memoryLogger.onTimerFired();
		assertEquals(2L, memoryLogger.getCount());
		verify(consumer, never()).addProfileData(any(ProfileData.class));
	}
	
	/**
	 * Validate the MemroryLogger stats have been reset.
	 * 
	 */
	void validateStatsReset(){
		assertEquals(0L, memoryLogger.getCount());
		assertEquals(0L, memoryLogger.getSum());
		assertEquals(0L, memoryLogger.getMaximum());
		assertEquals(Long.MAX_VALUE, memoryLogger.getMinimum());
	}
	
}
