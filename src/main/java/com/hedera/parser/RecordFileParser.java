
package com.hedera.parser;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.protobuf.TextFormat;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.recordFileLogger.LoggerStatus;
import com.hedera.recordFileLogger.RecordFileLogger;
import com.hedera.recordFileLogger.RecordFileLogger.INIT_RESULT;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;


/**
 * This is a utility file to read back service record file generated by Hedera node
 */
public class RecordFileParser {

	private static final Logger log = LogManager.getLogger("recordfileparser");
	private static final Marker MARKER = MarkerManager.getMarker("SERVICE_RECORD");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");
	static final int RECORD_FORMAT_VERSION = 2;
	static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	static final byte TYPE_RECORD = 2;          // next data type is transaction and its record
	static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed


	private static ConfigLoader configLoader = new ConfigLoader();
	private static LoggerStatus loggerStatus = new LoggerStatus();
	private static String thisFileHash = "";

	/**
	 * Given a service record name, read and parse and return as a list of service record pair
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @return return previous file hash
	 */
	static public boolean loadRecordFile(String fileName, String previousFileHash) {

		File file = new File(fileName);
		FileInputStream stream = null;
		String newFileHash = "";

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return false;
		}

		byte[] readFileHash = new byte[48];
		INIT_RESULT initFileResult = RecordFileLogger.initFile(fileName);
		if (initFileResult == INIT_RESULT.OK) {
			try {
				long counter = 0;
				MessageDigest md = MessageDigest.getInstance("SHA-384");
				MessageDigest mdForContent = MessageDigest.getInstance("SHA-384");
				stream = new FileInputStream(file);
				DataInputStream dis = new DataInputStream(stream);

				int record_format_version = dis.readInt();
				int version = dis.readInt();

				md.update(Utility.integerToBytes(record_format_version));
				md.update(Utility.integerToBytes(version));

				log.info(MARKER, "Record file format version " + record_format_version);
				log.info(MARKER, "HAPI protocol version " + version);

				while (dis.available() != 0) {

					try {
						byte typeDelimiter = dis.readByte();

						switch (typeDelimiter) {
							case TYPE_PREV_HASH:
								md.update(typeDelimiter);
								dis.read(readFileHash);
								md.update(readFileHash);

								if (Utility.hashIsEmpty(previousFileHash)) {
									log.error(MARKER, "Previous file Hash not available");
									previousFileHash = Hex.encodeHexString(readFileHash);
								} else {
									log.info(MARKER, "Previous file Hash = " + previousFileHash);
								}
								newFileHash = Hex.encodeHexString(readFileHash);
								log.info(MARKER, "New file Hash = " + newFileHash);

								if (!newFileHash.contentEquals(previousFileHash)) {

									if (configLoader.getStopLoggingIfRecordHashMismatchAfter().compareTo(Utility.getFileName(fileName)) < 0) {
										// last file for which mismatch is allowed is in the past
										log.error(MARKER, "Previous file Hash Mismatch - stopping loading. Previous = {}, Current = {}", previousFileHash, newFileHash);
										log.error(MARKER, "Mismatching file {}", fileName);
										return false;
									}
								}

								break;
							case TYPE_RECORD:
								int byteLength = dis.readInt();
								byte[] rawBytes = new byte[byteLength];

								dis.readFully(rawBytes);
								if (record_format_version >= RECORD_FORMAT_VERSION) {
									mdForContent.update(typeDelimiter);
									mdForContent.update(Utility.integerToBytes(byteLength));
									mdForContent.update(rawBytes);

								} else {
									md.update(typeDelimiter);
									md.update(Utility.integerToBytes(byteLength));
									md.update(rawBytes);
								}
								Transaction transaction = Transaction.parseFrom(rawBytes);

								byteLength = dis.readInt();
								rawBytes = new byte[byteLength];
								dis.readFully(rawBytes);

								if (record_format_version >= RECORD_FORMAT_VERSION) {
									mdForContent.update(Utility.integerToBytes(byteLength));
									mdForContent.update(rawBytes);

								} else {
									md.update(Utility.integerToBytes(byteLength));
									md.update(rawBytes);
								}

								TransactionRecord txRecord = TransactionRecord.parseFrom(rawBytes);

								counter++;

								if (RecordFileLogger.storeRecord(counter, Utility.convertToInstant(txRecord.getConsensusTimestamp()), transaction, txRecord, configLoader)) {
									log.info(MARKER, "record counter = {}\n=============================", counter);
									log.info(MARKER, "Transaction Consensus Timestamp = {}\n", Utility.convertToInstant(txRecord.getConsensusTimestamp()));
									log.info(MARKER, "Transaction = {}", Utility.printTransaction(transaction));
									log.info(MARKER, "Record = {}\n=============================\n",  TextFormat.shortDebugString(txRecord));
									break;
								} else {
									RecordFileLogger.rollback();
									break;
								}

							default:
								log.error(LOGM_EXCEPTION, "Exception Unknown record file delimiter {}", typeDelimiter);
						}


					} catch (Exception e) {
						log.error(LOGM_EXCEPTION, "Exception {}", e);
						RecordFileLogger.rollback();
						dis.close();
						return false;
					}
				}
				dis.close();

				if (record_format_version >= RECORD_FORMAT_VERSION) {
					md.update(mdForContent.digest());
				}
				byte[] fileHash = md.digest();
				thisFileHash = Utility.bytesToHex(fileHash);

				log.info("Calculated File hash for the current file {}", thisFileHash);

				RecordFileLogger.completeFile(thisFileHash, previousFileHash);
			} catch (FileNotFoundException e) {
				log.error(MARKER, "File Not Found Error {}", e);
				return false;
			} catch (IOException e) {
				log.error(MARKER, "IOException Error {}", e);
				return false;
			} catch (Exception e) {
				log.error(MARKER, "Parsing Error {}", e);
				return false;
			} finally {
				try {
					if (stream != null)
						stream.close();
				} catch (IOException ex) {
					log.error("Exception in close the stream {}", ex);
				}
			}
			loggerStatus.setLastProcessedRcdHash(thisFileHash);
			loggerStatus.saveToFile();
			return true;
		} else if (initFileResult == INIT_RESULT.SKIP) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * read and parse a list of record files
	 */
	static public void loadRecordFiles(List<String> fileNames) {

		String prevFileHash = loggerStatus.getLastProcessedRcdHash();

		for (String name : fileNames) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				return;
			}

			if (loadRecordFile(name, prevFileHash)) {
				prevFileHash = thisFileHash;
				Utility.moveFileToParsedDir(name, "/parsedRecordFiles/");
			} else {
				return;
			}
		}
	}

	public static void parseNewFiles(String pathName) {
		if (RecordFileLogger.start()) {

			File file = new File(pathName);
			if ( ! file.exists()) {
				file.mkdirs();
			}

			if (file.isDirectory()) { //if it's a directory

				String[] files = file.list(); // get all files under the directory
				Arrays.sort(files);           // sorted by name (timestamp)

				// add directory prefix to get full path
				List<String> fullPaths = Arrays.asList(files).stream()
						.filter(f -> Utility.isRecordFile(f))
						.map(s -> file + "/" + s)
						.collect(Collectors.toList());

				log.info(MARKER, "Loading record files from directory {} ", pathName);

				if (fullPaths != null && fullPaths.size()!= 0) {
					log.info(MARKER, "Files are " + fullPaths);
					loadRecordFiles(fullPaths);
				} else {
					log.info(MARKER, "No files to parse");
					return;
				}
			} else {
				log.error(LOGM_EXCEPTION, "Input parameter {} is not a folder", pathName);
				return;

			}
			RecordFileLogger.finish();
		}
	}

	public static void main(String[] args) {
		String pathName;

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, exiting.");
				System.exit(0);
			}

			configLoader = new ConfigLoader();

			pathName = configLoader.getDefaultParseDir(OPERATION_TYPE.RECORDS);
			log.info(MARKER, "Record files folder got from configuration file: {}", pathName);

			if (pathName != null) {
				parseNewFiles(pathName);
			}
		}
	}

	/**
	 * Given a service record name, read its prevFileHash
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @return return previous file hash's Hex String
	 */
	static public String readPrevFileHash(String fileName) {
		File file = new File(fileName);
		FileInputStream stream = null;
		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return null;
		}
		byte[] prevFileHash = new byte[48];
		try {
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);

			// record_format_version
			dis.readInt();
			// version
			dis.readInt();

			byte typeDelimiter = dis.readByte();

			if (typeDelimiter == TYPE_PREV_HASH) {
				dis.read(prevFileHash);
				String hexString = Hex.encodeHexString(prevFileHash);
				log.info(MARKER, "readPrevFileHash :: Previous file Hash = {}, file name = {}", hexString, fileName);
				dis.close();
				return hexString;
			} else {
				log.error(MARKER, "readPrevFileHash :: Should read Previous file Hash, but found file delimiter {}, file name = {}", typeDelimiter, fileName);
			}
			dis.close();

		} catch (FileNotFoundException e) {
			log.error(MARKER, "readPrevFileHash :: File Not Found Error, file name = {}",  fileName);
		} catch (IOException e) {
			log.error(MARKER, "readPrevFileHash :: IOException Error, file name = {}, Exception {}",  fileName, e);
		} catch (Exception e) {
			log.error(MARKER, "readPrevFileHash :: Parsing Error, file name = {}, Exception {}",  fileName, e);
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error("readPrevFileHash :: Exception in close the stream {}", ex);
			}
		}

		return null;
	}
}
