package com.careslot.appointment;
public class AppointmentConflictException extends RuntimeException { public AppointmentConflictException() { super("That time is no longer available."); } }
