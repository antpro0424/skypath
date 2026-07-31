package com.spotnana.flightsearch.api.dto;

/** An airport, as offered to the interface for autocomplete and labelling. */
public record AirportView(String code, String name, String city, String country, String timezone) {}
