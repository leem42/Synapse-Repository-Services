package org.sagebionetworks.repo.manager.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.sagebionetworks.repo.model.daemon.BackupAliasType;

/**
 * Abstraction for streaming migration backup files, for both creation an
 * reading.
 * 
 */
public interface BackupFileStream {

	/**
	 * Stream over all of the data in the given backup file InputStream. The data is
	 * read from the provided stream, one sub-file at a time. This means each
	 * sub-file must be small enough to fit in memory. After each sub-file is read,
	 * all data from that file will be flushed from memory.
	 * 
	 * @param input
	 * @return
	 */
	public Iterable<RowData> readBackupFile(InputStream input, BackupAliasType backupAliasType);

	/**
	 * Stream over the provide data to write the given backup file OutputStream.
	 * 
	 * @param out
	 * @param stream
	 * @param maximumRowsPerFile
	 *            Determines the maximum number of rows that will reside in memory
	 *            during both reading and writing migration backup files. Each time
	 *            this number of rows is read from the provided stream, the current
	 *            batch of rows will be written as a new sub-file within the zip,
	 *            the rows will be cleared from memory, and a new batch of rows will
	 *            be started.
	 * @throws IOException
	 */
	public void writeBackupFile(OutputStream out, Iterable<RowData> stream, BackupAliasType backupAliasType,
			int maximumRowsPerFile) throws IOException;
}
