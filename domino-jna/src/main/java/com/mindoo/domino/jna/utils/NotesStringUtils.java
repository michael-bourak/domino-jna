package com.mindoo.domino.jna.utils;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Formatter;

import com.mindoo.domino.jna.errors.NotesErrorUtils;
import com.mindoo.domino.jna.internal.NotesCAPI;
import com.mindoo.domino.jna.internal.NotesJNAContext;
import com.mindoo.domino.jna.internal.WinNotesCAPI;
import com.mindoo.domino.jna.structs.NotesOriginatorId;
import com.mindoo.domino.jna.structs.NotesUniversalNoteId;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * String conversion functions between Java and LMBCS
 * 
 * @author Karsten Lehmann
 */
public class NotesStringUtils {

	/**
	 * Scans the Memory object for null values
	 * 
	 * @param in memory
	 * @return number of bytes before null byte in memory
	 */
	public static int getNullTerminatedLength(Memory in) {
		int textLen = (int) in.size();
		
		//search for terminating null character
		for (int i=0; i<textLen; i++) {
			byte b = in.getByte(i);
			if (b==0) {
				textLen = i;
				break;
			}
		}

		return textLen;
	}

	/**
	 * Converts an LMBCS string to a Java String
	 * 
	 * @param inPtr pointer in memory
	 * @param textLen length of text
	 * @return decoded String
	 */
	public static String fromLMBCS(Pointer inPtr, long textLen) {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();

		if (textLen==0) {
			return "";
		}
		
		Pointer pText = inPtr;
		
		Memory pBuf_utf8 = null;
		
		StringBuilder result = new StringBuilder();
		while (textLen > 0) {
			long len=(textLen>NotesCAPI.MAXPATH) ? NotesCAPI.MAXPATH : textLen;
			long outLen=2*len;
			
			if (pBuf_utf8==null || pBuf_utf8.size()!=(outLen+1)) {
				pBuf_utf8 = new Memory(outLen+1);
			}

			//convert text from LMBCS to utf8
			int len_utf8 = notesAPI.OSTranslate(NotesCAPI.OS_TRANSLATE_LMBCS_TO_UTF8, pText, (short) (len & 0xffff), pBuf_utf8, (short) (outLen & 0xffff));
			pBuf_utf8.setByte(len_utf8, (byte) 0);
			
			// copy 
			String currConvertedStr;
			try {
				currConvertedStr = new String(pBuf_utf8.getByteArray(0, len_utf8), 0, len_utf8, "UTF-8");
				if (currConvertedStr.contains("\0")) {
					//Notes uses \0 for multiline strings
					if (notesAPI instanceof WinNotesCAPI) {
						currConvertedStr = currConvertedStr.replace("\0", "\r\n");
					}
					else {
						currConvertedStr = currConvertedStr.replace("\0", "\n");
					}
				}
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("Unknown encoding UTF-8", e);
			}
			
			textLen -= len;
			
			//shortcut for short strings
			if (result==null && textLen<=0) {
				return currConvertedStr;
			}
			
			if (result==null) {
				result = new StringBuilder();
			}
			result.append(currConvertedStr);
			
			pText = pText.share(len);
		}
		return result==null ? "" : result.toString();
	}

	/**
	 * Converts a string to LMBCS format
	 * 
	 * @param inStr string
	 * @param addNull tre to terminate the string with a null byte
	 * @return encoded string in memory
	 */
	public static Memory toLMBCS(String inStr, boolean addNull) {
		if (inStr==null)
			return null;
		
		if (inStr.length()==0) {
			if (addNull) {
				Memory m = new Memory(1);
				m.setByte(0, (byte) 0);
				return m;				
			}
			else {
				return null;
			}
		}
		
		if (inStr.contains("\n")) {
			//replace line breaks with null characters
			String[] lines = inStr.split("\\r?\\n", -1);
			StringBuilder sb = new StringBuilder();
			for (int i=0; i<lines.length; i++) {
				if (i>0) {
					sb.append('\0');
				}
				sb.append(lines[i]);
			}
			inStr = sb.toString();
		}
		
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();

		String currRemainingStr = inStr;
		
		final int maxStrSize = 32767;
		
		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		
		while (currRemainingStr.length()>0) {
			//decide how much text we want to process; we need to do some calculations
			//to not exceed the max buffer size for the UTF-8 characters of 65535 bytes (length is specified as WORD)
			int numWorkCharacters = Math.min(currRemainingStr.length(), maxStrSize);
			
			@SuppressWarnings("unused")
			int remainingStrUtf8Size;
			while ((remainingStrUtf8Size = StringUtil.stringLengthInUTF8(currRemainingStr.substring(0, numWorkCharacters))) > 32767) {
				numWorkCharacters -= 10;
			}
			
			String currWorkStr = currRemainingStr.substring(0, numWorkCharacters);
			currRemainingStr = currRemainingStr.substring(numWorkCharacters);
			
			
			byte[] currWorkStrAsBytes;
			try {
				currWorkStrAsBytes = currWorkStr.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("Unknown encoding UTF-8", e);
			}
			Memory in = new Memory(currWorkStrAsBytes.length);
			in.write(0, currWorkStrAsBytes, 0, currWorkStrAsBytes.length);

			Memory out = new Memory(in.size() * 2);
			if (out.size() >= 65535) {
				throw new IllegalStateException("out buffer is expected to be in WORD range. "+out.size()+" >= 65535");
			}
			
			short outContentLength = notesAPI.OSTranslate(NotesCAPI.OS_TRANSLATE_UTF8_TO_LMBCS, in, (short) (in.size() & 0xffff), out, (short) (out.size() & 0xffff));
			byte[] outAsBytes = new byte[outContentLength];
			
			out.read(0, outAsBytes, 0, outContentLength);
			bOut.write(outAsBytes, 0, outContentLength);
		}
		
		if (addNull) {
			Memory all = new Memory(bOut.size()+1);
			byte[] allAsBytes = bOut.toByteArray();
			all.write(0, allAsBytes, 0, bOut.size());
			all.setByte(all.size()-1, (byte) 0); 
			return all;			
		}
		else {
			Memory all = new Memory(bOut.size());
			byte[] allAsBytes = bOut.toByteArray();
			all.write(0, allAsBytes, 0, bOut.size());
			return all;
		}
	}

	/**
	 * Extracts the UNID from a {@link NotesUniversalNoteId} and converts it to a string
	 * 
	 * @param unid UNID object
	 * @return UNID string
	 */
	public static String extractUNID(NotesUniversalNoteId unid) {
		unid.write();
		Pointer oidPtr = unid.getPointer();
		
		Formatter formatter = new Formatter();
		ByteBuffer data = oidPtr.getByteBuffer(0, 16).order(ByteOrder.LITTLE_ENDIAN);
		formatter.format("%16x", data.getLong());
		formatter.format("%16x", data.getLong());
		String unidStr = formatter.toString().toUpperCase();
		formatter.close();
		return unidStr;
	}
	
	/**
	 * Extracts the UNID from a {@link NotesOriginatorId} and converts it to a string
	 * 
	 * @param oid originator id
	 * @return unid
	 */
	public static String extractUNID(NotesOriginatorId oid) {
		oid.write();
		Pointer oidPtr = oid.getPointer();
		
		Formatter formatter = new Formatter();
		ByteBuffer data = oidPtr.getByteBuffer(0, 16).order(ByteOrder.LITTLE_ENDIAN);
		formatter.format("%16x", data.getLong());
		formatter.format("%16x", data.getLong());
		String unid = formatter.toString().replace(" ", "0").toUpperCase();
		formatter.close();
		return unid;
	}
	
	/**
	 * Converts bytes in memory to a UNID
	 * 
	 * @param innardsFile innards of file part
	 * @param innardsNote innards of note part
	 * @return unid
	 */
	public static String toUNID(long innardsFile, long innardsNote) {
		Formatter formatter = new Formatter();
		
		formatter.format("%16x", innardsFile);
		formatter.format("%16x", innardsNote);
		String unid = formatter.toString().toUpperCase();
		formatter.close();
		return unid;
	}
	
	/**
	 * Converts bytes in memory to a UNID
	 * 
	 * @param buf memory
	 * @return unid
	 */
	public static String toUNID(Memory buf) {
		Formatter formatter = new Formatter();
		ByteBuffer data = buf.getByteBuffer(0, buf.size()).order(ByteOrder.LITTLE_ENDIAN);
		formatter.format("%16x", data.getLong());
		formatter.format("%16x", data.getLong());
		String unid = formatter.toString().toUpperCase();
		formatter.close();
		return unid;
	}

	/**
	 * This function takes a port name, a server name, and file path relative to the Domino or
	 * Notes data directory and creates a full network path specification for a Domino database
	 * file.<br>
	 * <br>
	 * To open a Domino database on a server, use this function to create the full path specification,
	 * and pass this specification as input to NSFDbOpen or NSFDbOpenExtended.
	 * 
	 * @param portName network port name or NULL to allow Domino or Notes to use the "most available" port to the given server
	 * @param serverName Name of the server (either in abbreviated format, canonical format or as common name)  or "" for local
	 * @param fileName filename of the Domino database you with to access, relative to the data directory
	 * @return fully qualified network path
	 */
	public static String osPathNetConstruct(String portName, String serverName, String fileName) {
		Memory portNameMem = toLMBCS(portName, true);
		Memory serverNameMem = toLMBCS(serverName, true);
		Memory fileNameMem = toLMBCS(fileName, true);
		
		Memory retPathMem = new Memory(NotesCAPI.MAXPATH);
		
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		short result = notesAPI.OSPathNetConstruct(portNameMem, serverNameMem, fileNameMem, retPathMem);
		NotesErrorUtils.checkResult(result);
		String retPath = fromLMBCS(retPathMem, getNullTerminatedLength(retPathMem));
		return retPath;
	}

	/**
	 * Given a fully-qualified network path to a Domino database file, this function breaks it
	 * into its port name, server name, and filename components.<br>
	 * If the fully qualified path contains just the port name and/or server name components,
	 * then they will be the only ones returned.<br>
	 * <br>
	 * Expanded database filepath syntax:<br>
	 * <br>
	 * {Port} NetworkSeparator {servername} Serversuffix {filename}<br>
	 * COM! {NetworkSeparator} NOTESBETA {ServerSuffix} NOTEFILE\APICOMMS.NSF<br>
	 * <br>
	 * Note: the NetworkSeparator and ServerSuffix are not system independent. To maintain the
	 * portability of your code, it is recommended that you make no explicit use of them
	 * anywhere in your programs.
	 * 
	 * @param pathName expanded path specification of a Domino database file
	 * @return String array of portname, servername, filename
	 */
	public static String[] osPathNetParse(String pathName) {
		Memory retPortNameMem = new Memory(NotesCAPI.MAXPATH);
		Memory retServerNameMem = new Memory(NotesCAPI.MAXPATH);
		Memory retFileNameMem = new Memory(NotesCAPI.MAXPATH);
		
		Memory pathNameMem = toLMBCS(pathName, true);
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		short result = notesAPI.OSPathNetParse(pathNameMem, retPortNameMem, retServerNameMem, retFileNameMem);
		NotesErrorUtils.checkResult(result);
		
		String portName = fromLMBCS(retPortNameMem, getNullTerminatedLength(retPortNameMem));
		String serverName = fromLMBCS(retServerNameMem, getNullTerminatedLength(retServerNameMem));
		String fileName = fromLMBCS(retFileNameMem, getNullTerminatedLength(retFileNameMem));
		return new String[] {portName, serverName, fileName};
	}

	/**
	 * Converts an innards array to hex format, e.g. used for replica ids
	 * 
	 * @param innards innards array with two elements
	 * @return replica id (16 character hex string)
	 */
	public static String innardsToReplicaId(int[] innards) {
		return StringUtil.pad(Integer.toHexString(innards[1]).toUpperCase(), 8, '0', false) +
				StringUtil.pad(Integer.toHexString(innards[0]).toUpperCase(), 8, '0', false);
	}

	/**
	 * Converts a replica id to an innards array
	 * 
	 * @param replicaId replica id
	 * @return innards array with two elements
	 */
	public static int[] replicaIdToInnards(String replicaId) {
		if (replicaId.contains(":"))
			replicaId = replicaId.replace(":", "");
		
		if (replicaId.length() != 16) {
			throw new IllegalArgumentException("Replica ID is expected to have 16 hex characters or 8:8 format");
		}
		
		int[] innards = new int[2];
		innards[1] = (int) (Long.parseLong(replicaId.substring(0,8), 16) & 0xffffffff);
		innards[0] = (int) (Long.parseLong(replicaId.substring(8), 16) & 0xffffffff);
		
		return innards;
	}
}
