"use client";

import { useId, useState } from "react";
import type { Airport, SearchQuery } from "@/lib/types";
import styles from "./SearchForm.module.css";

/**
 * The dataset covers 15–16 March 2024 only, so defaulting to today would open the page in
 * a state that can never return a result.
 */
export const DEFAULT_SEARCH_DATE = "2024-03-15";

const AIRPORT_CODE = /^[A-Z]{3}$/;

export interface FieldErrors {
  origin?: string;
  destination?: string;
  date?: string;
}

interface SearchFormProps {
  airports: Airport[];
  isSearching: boolean;
  onSearch: (query: SearchQuery) => void;
}

export function SearchForm({ airports, isSearching, onSearch }: SearchFormProps) {
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [date, setDate] = useState(DEFAULT_SEARCH_DATE);
  const [errors, setErrors] = useState<FieldErrors>({});

  const listId = useId();
  const originId = useId();
  const destinationId = useId();
  const dateId = useId();

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const query = { origin: origin.trim(), destination: destination.trim(), date };
    const found = validate(query);
    setErrors(found);

    // Nothing is requested while the form is invalid, so an obvious mistake never
    // costs a round trip.
    if (Object.keys(found).length === 0) {
      onSearch(query);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <div className={styles.fields}>
        <Field
          id={originId}
          label="From"
          value={origin}
          error={errors.origin}
          listId={listId}
          placeholder="JFK"
          onChange={(value) => setOrigin(value.toUpperCase())}
        />
        <Field
          id={destinationId}
          label="To"
          value={destination}
          error={errors.destination}
          listId={listId}
          placeholder="LAX"
          onChange={(value) => setDestination(value.toUpperCase())}
        />

        <div className={styles.field}>
          <label className={styles.label} htmlFor={dateId}>
            Departure date
          </label>
          <input
            className={styles.input}
            id={dateId}
            type="date"
            value={date}
            aria-invalid={errors.date ? true : undefined}
            aria-describedby={errors.date ? `${dateId}-error` : undefined}
            onChange={(event) => setDate(event.target.value)}
          />
          {errors.date && (
            <p className={styles.error} id={`${dateId}-error`} role="alert">
              {errors.date}
            </p>
          )}
        </div>

        <button className={styles.submit} type="submit" disabled={isSearching}>
          {isSearching ? "Searching…" : "Search flights"}
        </button>
      </div>

      <datalist id={listId}>
        {airports.map((airport) => (
          <option key={airport.code} value={airport.code}>
            {airport.city} — {airport.name}
          </option>
        ))}
      </datalist>

      <p className={styles.hint}>
        The sample schedule covers 15–16 March 2024.
      </p>
    </form>
  );
}

interface FieldProps {
  id: string;
  label: string;
  value: string;
  error?: string;
  listId: string;
  placeholder: string;
  onChange: (value: string) => void;
}

function Field({ id, label, value, error, listId, placeholder, onChange }: FieldProps) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      <input
        className={styles.input}
        id={id}
        type="text"
        value={value}
        list={listId}
        placeholder={placeholder}
        maxLength={3}
        autoComplete="off"
        spellCheck={false}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && (
        <p className={styles.error} id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

function validate(query: SearchQuery): FieldErrors {
  const errors: FieldErrors = {};

  if (!query.origin) {
    errors.origin = "Enter a departure airport.";
  } else if (!AIRPORT_CODE.test(query.origin)) {
    errors.origin = "Use a three-letter airport code, such as JFK.";
  }

  if (!query.destination) {
    errors.destination = "Enter an arrival airport.";
  } else if (!AIRPORT_CODE.test(query.destination)) {
    errors.destination = "Use a three-letter airport code, such as LAX.";
  }

  if (!errors.origin && !errors.destination && query.origin === query.destination) {
    errors.destination = "Departure and arrival airports must be different.";
  }

  if (!query.date) {
    errors.date = "Choose a departure date.";
  }

  return errors;
}
