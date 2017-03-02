package org.sagebionetworks.repo.model.dbo.dao.dataaccess;

import org.sagebionetworks.repo.model.dataaccess.ResearchProject;
import org.sagebionetworks.repo.web.NotFoundException;

public interface ResearchProjectDAO {

	/**
	 * Create a new ResearchProject.
	 * 
	 * @param toCreate
	 * @return
	 */
	public ResearchProject create(ResearchProject toCreate);

	/**
	 * Retrieve a ResearchProject given the accessRequirementId and ownerId.
	 * 
	 * @param accessRequirementId
	 * @param ownerId
	 * @return
	 * @throws NotFoundException if the ResearchProject cannot be found.
	 */
	public ResearchProject get(String accessRequirementId, String ownerId) throws NotFoundException;

	/**
	 * Update an existing ResearchProject.
	 * 
	 * @param toUpdate
	 * @return
	 * @throws NotFoundException if the ResearchProject cannot be found.
	 */
	public ResearchProject update(ResearchProject toUpdate) throws NotFoundException;

	/**
	 * Delete a ResearchProject given its ID.
	 * 
	 * @param id
	 */
	void delete(String id);
}
