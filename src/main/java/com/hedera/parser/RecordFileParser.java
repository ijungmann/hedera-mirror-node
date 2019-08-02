package com.hedera.parser;

import com.google.protobuf.TextFormat;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirrorNodeProxy.Utility;
import com.hedera.recordFileLogger.LoggerStatus;
import com.hedera.recordFileLogger.RecordFileLogger;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * This is a utility file to read back service record file generated by Hedera node
 */
public class RecordFileParser {

	private static final Logger log = LogManager.getLogger("recordStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("SERVICE_RECORD");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	static final byte TYPE_RECORD = 2;          // next data type is transaction and its record
	static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed

	private static ConfigLoader configLoader;
	private static LoggerStatus loggerStatus;

	private enum LoadResult {
		OK
		,STOP
		,ERROR
	}

	private static final String PARSED_DIR = "/parsedRecordFiles/";

	/**
	 * Given a service record name, read and parse and return as a list of service record pair
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @return return previous file hash
	 */
	static public LoadResult loadRecordFile(String fileName, String previousFileHash) {

		File file = new File(fileName);
		FileInputStream stream = null;
		String newFileHash = "";

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return null;
		}

		if (RecordFileLogger.initFile(fileName)) {

			try {
				long counter = 0;
				stream = new FileInputStream(file);
				DataInputStream dis = new DataInputStream(stream);

				int record_format_version = dis.readInt();
				int version = dis.readInt();

				log.info(MARKER, "Record file format version " + record_format_version);
				log.info(MARKER, "HAPI protocol version " + version);

				while (dis.available() != 0) {

					try {
						byte typeDelimiter = dis.readByte();

						switch (typeDelimiter) {
							case TYPE_PREV_HASH:
								byte[] readFileHash = new byte[48];
								dis.read(readFileHash);
								if (previousFileHash.isEmpty()) {
									log.error(MARKER, "Previous file Hash not available");
									previousFileHash = Hex.encodeHexString(readFileHash);
								} else {
									log.info(MARKER, "Previous file Hash = " + previousFileHash);
								}
								newFileHash = Hex.encodeHexString(readFileHash);
								log.info(MARKER, "New file Hash = " + newFileHash);

								if (!newFileHash.contentEquals(previousFileHash)) {
									if (configLoader.getStopLoggingIfHashMismatch()) {
										log.error(MARKER, "Previous file Hash Mismatch - stopping loading. Previous = {}, Current = {}", previousFileHash, newFileHash);
										return LoadResult.STOP;
									}
								}

								break;
							case TYPE_RECORD:
								int byteLength = dis.readInt();
								byte[] rawBytes = new byte[byteLength];

								dis.readFully(rawBytes);
								Transaction transaction = Transaction.parseFrom(rawBytes);

								byteLength = dis.readInt();
								rawBytes = new byte[byteLength];
								dis.readFully(rawBytes);
								TransactionRecord txRecord = TransactionRecord.parseFrom(rawBytes);

								counter++;

								if (RecordFileLogger.storeRecord(counter, Utility.convertToInstant(txRecord.getConsensusTimestamp()), transaction, txRecord, configLoader)) {
									log.info(MARKER, "record counter = {}\n=============================", counter);
									log.info(MARKER, "Transaction Consensus Timestamp = {}\n", Utility.convertToInstant(txRecord.getConsensusTimestamp()));
									log.info(MARKER, "Transaction = {}", Utility.printTransaction(transaction));
									log.info(MARKER, "Record = {}\n=============================\n",  TextFormat.shortDebugString(txRecord));
									break;
								} else {
									return LoadResult.ERROR;
								}
							case TYPE_SIGNATURE:
								int sigLength = dis.readInt();
								log.info(MARKER, "sigLength = " + sigLength);
								byte[] sigBytes = new byte[sigLength];
								dis.readFully(sigBytes);
								log.info(MARKER, "File {} Signature = {} ", fileName, Hex.encodeHexString(sigBytes));
								if (RecordFileLogger.storeSignature(Hex.encodeHexString(sigBytes))) {
									break;
								} else {
									return LoadResult.ERROR;
								}

							default:
								log.error(LOGM_EXCEPTION, "Exception Unknown record file delimiter {}", typeDelimiter);
						}

					} catch (Exception e) {
						log.error(LOGM_EXCEPTION, "Exception ", e);
						return LoadResult.ERROR;
					}
				}
				dis.close();
			} catch (FileNotFoundException e) {
				log.error(MARKER, "File Not Found Error");
				return LoadResult.ERROR;
			} catch (IOException e) {
				log.error(MARKER, "IOException Error");
				return LoadResult.ERROR;
			} catch (Exception e) {
				log.error(MARKER, "Parsing Error");
				return LoadResult.ERROR;
			} finally {
				try {
					if (stream != null)
						stream.close();
					if (!RecordFileLogger.completeFile()) {
						return LoadResult.ERROR;
					}
				} catch (IOException ex) {
					log.error("Exception in close the stream {}", ex);
				}
			}
			loggerStatus.setLastProcessedRcdHash(newFileHash);
			loggerStatus.saveToFile();
			return LoadResult.OK;
		} else {
			return LoadResult.ERROR;
		}

	}

	/**
	 * read and parse a list of record files
	 */
	static public void loadRecordFiles(List<String> fileNames) {

		String prevFileHash = loggerStatus.getLastProcessedRcdHash();

		for (String name : fileNames) {
			LoadResult result = loadRecordFile(name, prevFileHash);

			if (result == LoadResult.STOP) {
				return;
			}

			prevFileHash = Utility.bytesToHex(Utility.getFileHash(name));
			if (result == LoadResult.OK) {
				Utility.moveFileToParsedDir(name, PARSED_DIR);
			}
		}
	}

	public static void main(String[] args) {
		String pathName;

		configLoader = new ConfigLoader("./config/config.json");
		loggerStatus = new LoggerStatus("./config/loggerStatus.json");

		pathName = configLoader.getDefaultParseDir_RecordStream();
		log.info(MARKER, "Record files folder got from configuration file: {}", configLoader.getDefaultParseDir_RecordStream());

		if (pathName != null) {

			if (RecordFileLogger.start()) {

				File file = new File(pathName);
				if (file.isFile()) {
					log.info(MARKER, "Loading record file {} " + pathName);
					loadRecordFile(pathName, "");
				} else if (file.isDirectory()) { //if it's a directory

					String[] files = file.list(); // get all files under the directory
					Arrays.sort(files);           // sorted by name (timestamp)

					// add director prefix to get full path
					List<String> fullPaths = Arrays.asList(files).stream()
							.filter(f -> Utility.isRecordFile(f))
							.map(s -> file + "/" + s)
							.collect(Collectors.toList());

					log.info(MARKER, "Loading record files from directory {} ", pathName);

					if (fullPaths != null) {
						log.info(MARKER, "Files are " + fullPaths);
						loadRecordFiles(fullPaths);
					} else {
						log.info(MARKER, "No files to parse");
					}
				} else {
					log.error(LOGM_EXCEPTION, "Exception file {} does not exist", pathName);

				}
				RecordFileLogger.finish();
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
			log.error(MARKER, "readPrevFileHash :: IOException Error, file name = {}",  fileName);
		} catch (Exception e) {
			log.error(MARKER, "readPrevFileHash :: Parsing Error, file name = {}",  fileName);
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