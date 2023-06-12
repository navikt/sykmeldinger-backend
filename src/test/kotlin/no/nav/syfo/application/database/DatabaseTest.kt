package no.nav.syfo.application.database

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.testutils.TestDB

internal class DatabaseTest : FunSpec({ test("Test database") { TestDB.database } })
