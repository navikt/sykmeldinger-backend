UPDATE sykmeldingstatus
SET sporsmal = jsonb_insert(sporsmal, '{-1}', '{
  "svar": "JA",
  "tekst": "Har du forsikring som gjelder for de første 16 dagene av sykefraværet?",
  "svartype": "JA_NEI",
  "shortName": "FORSIKRING"
}', true)
WHERE alle_sporsmal ->> 'fisker' IS NOT NULL
  AND timestamp > '2024-01-01 00:00:00'
  AND alle_sporsmal -> 'fisker' -> 'blad' ->> 'svar' = 'B'
  AND alle_sporsmal -> 'fisker' -> 'lottOgHyre' ->> 'svar' != 'HYRE';
