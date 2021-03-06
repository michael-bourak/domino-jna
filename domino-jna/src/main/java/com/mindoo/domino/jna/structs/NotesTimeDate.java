package com.mindoo.domino.jna.structs;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.mindoo.domino.jna.internal.NotesCAPI;
import com.mindoo.domino.jna.utils.NotesDateTimeUtils;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * JNA class for the TIMEDATE type
 * 
 * @author Karsten Lehmann
 */
public class NotesTimeDate extends BaseStructure implements Serializable {
	private static final long serialVersionUID = 549580185343880134L;
	
	/** C type : DWORD[2] */
	public int[] Innards = new int[2];
	public NotesTimeDate() {
		super();
	}
	protected List<? > getFieldOrder() {
		return Arrays.asList("Innards");
	}
	/** @param Innards C type : DWORD[2] */
	public NotesTimeDate(int Innards[]) {
		super();
		if ((Innards.length != this.Innards.length)) 
			throw new IllegalArgumentException("Wrong array size !");
		this.Innards = Innards;
	}
	public NotesTimeDate(Pointer peer) {
		super(peer);
	}
	
	@Override
	public String toString() {
		return NotesDateTimeUtils.toString(this);
	}
	
	public static class ByReference extends NotesTimeDate implements Structure.ByReference {
		
	};
	public static class ByValue extends NotesTimeDate implements Structure.ByValue {
		
	};
	
	/**
	 * Checks whether the timedate has a date portion
	 * 
	 * @return true if date part exists
	 */
	public boolean hasDate() {
        boolean hasDate=(Innards[1]!=0 && Innards[1]!=NotesCAPI.ANYDAY);
		return hasDate;
	}
	
	/**
	 * Checks whether the timedate has a time portion
	 * 
	 * @return true if time part exists
	 */
	public boolean hasTime() {
        boolean hasDate=(Innards[0]!=0 && Innards[0]!=NotesCAPI.ALLDAY);
		return hasDate;
	}
	
	/**
	 * Converts the time date to a calendar
	 * 
	 * @return calendar or null if data is invalid
	 */
	public Calendar toCalendar() {
		return NotesDateTimeUtils.innardsToCalendar(NotesDateTimeUtils.isDaylightTime(), NotesDateTimeUtils.getGMTOffset(), this.Innards);
	}
	
	/**
	 * Converts the time date to a Java {@link Date}
	 * 
	 * @return date or null if data is invalid
	 */
	public Date toDate() {
		Calendar cal = toCalendar();
		return cal==null ? null : cal.getTime();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof NotesTimeDate) {
			return Arrays.equals(this.Innards, ((NotesTimeDate)o).Innards);
		}
		return false;
	}
	
	/**
	 * Sets the date/time of this timedate to the current time
	 */
	public void setNow() {
		int[] newInnards = NotesDateTimeUtils.calendarToInnards(Calendar.getInstance(), true, true);
		this.Innards[0] = newInnards[0];
		this.Innards[1] = newInnards[1];
		write();
	}

	/**
	 * Sets the date part of this timedate to today and the time part to ALLDAY
	 */
	public void setToday() {
		int[] newInnards = NotesDateTimeUtils.calendarToInnards(Calendar.getInstance(), true, false);
		this.Innards[0] = newInnards[0];
		this.Innards[1] = newInnards[1];
		write();
	}

	/**
	 * Sets the date part of this timedate to tomorrow and the time part to ALLDAY
	 */
	public void setTomorrow() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, 1);
		int[] newInnards = NotesDateTimeUtils.calendarToInnards(cal, true, false);
		this.Innards[0] = newInnards[0];
		this.Innards[1] = newInnards[1];
		write();
	}

	/**
	 * Creates a new {@link NotesTimeDate} instance with the same data as this one
	 */
	public NotesTimeDate clone() {
		NotesTimeDate clone = new NotesTimeDate();
		clone.Innards[0] = this.Innards[0];
		clone.Innards[1] = this.Innards[1];
		clone.write();
		return clone;
	}
	
	/**
	 * Modifies the data by adding/subtracting values for year, month, day, hours, minutes and seconds
	 * 
	 * @param year positive or negative value or 0 for no change
	 * @param month positive or negative value or 0 for no change
	 * @param day positive or negative value or 0 for no change
	 * @param hours positive or negative value or 0 for no change
	 * @param minutes positive or negative value or 0 for no change
	 * @param seconds positive or negative value or 0 for no change
	 */
	public void adjust(int year, int month, int day, int hours, int minutes, int seconds) {
		Calendar cal = NotesDateTimeUtils.innardsToCalendar(NotesDateTimeUtils.isDaylightTime(), NotesDateTimeUtils.getGMTOffset(), this.Innards);
		if (cal!=null) {
			boolean modified = false;
			
			if (NotesDateTimeUtils.hasDate(cal)) {
				if (year!=0) {
					cal.add(Calendar.YEAR, year);
					modified=true;
				}
				if (month!=0) {
					cal.add(Calendar.MONTH, month);
					modified=true;
				}
				if (day!=0) {
					cal.add(Calendar.DATE, day);
					modified=true;
				}
			}
			if (NotesDateTimeUtils.hasTime(cal)) {
				if (hours!=0) {
					cal.add(Calendar.HOUR, hours);
					modified=true;
				}
				if (minutes!=0) {
					cal.add(Calendar.MINUTE, minutes);
					modified=true;
				}
				if (seconds!=0) {
					cal.add(Calendar.SECOND, seconds);
					modified=true;
				}
			}
			
			if (modified) {
				int[] newInnards = NotesDateTimeUtils.calendarToInnards(cal);
				this.Innards[0] = newInnards[0];
				this.Innards[1] = newInnards[1];
				write();
			}
		}
	}
}
