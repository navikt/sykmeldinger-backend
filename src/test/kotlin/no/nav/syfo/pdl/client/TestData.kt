package no.nav.syfo.pdl.client

fun getTestData(): String {
    return "{\n" +
        "  \"data\": {\n" +
        "    \"person\": {\n" +
        "      \"navn\": [\n" +
        "        {\n" +
        "          \"fornavn\": \"RASK\",\n" +
        "          \"mellomnavn\": null,\n" +
        "          \"etternavn\": \"SAKS\"\n" +
        "        }\n" +
        "      ],\n" +
        "     \"adressebeskyttelse\": [\n" +
        "        {\n" +
        "          \"gradering\": \"UGRADERT\"\n" +
        "        }\n" +
        "      ]\n" +
        "    },\n" +
        "    \"identer\": {\n" +
        "      \"identer\": [\n" +
        "        {\n" +
        "          \"ident\": \"99999999999\",\n" +
        "          \"gruppe\": \"AKTORID\"\n" +
        "        },\n" +
        "        {\n" +
        "          \"ident\": \"12345678901\",\n" +
        "          \"gruppe\": \"FOLKEREGISTERIDENT\"\n" +
        "        }\n" +
        "      ]\n" +
        "    }\n" +
        "  }\n" +
        "}"
}

fun getErrorResponse(): String {
    return "{\n" +
        "  \"errors\": [\n" +
        "    {\n" +
        "      \"message\": \"Ikke tilgang til å se person\",\n" +
        "      \"locations\": [\n" +
        "        {\n" +
        "          \"line\": 2,\n" +
        "          \"column\": 3\n" +
        "        }\n" +
        "      ],\n" +
        "      \"path\": [\n" +
        "        \"hentPerson\"\n" +
        "      ],\n" +
        "      \"extensions\": {\n" +
        "        \"code\": \"unauthorized\",\n" +
        "        \"classification\": \"ExecutionAborted\"\n" +
        "      }\n" +
        "    }\n" +
        "  ],\n" +
        "  \"data\": {\n" +
        "    \"hentPerson\": null\n" +
        "  }\n" +
        "}"
}
