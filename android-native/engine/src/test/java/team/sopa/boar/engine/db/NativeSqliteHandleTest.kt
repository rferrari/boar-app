package team.sopa.boar.engine.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge's open and prepare return a handle (a pointer) or an SQLite code negated. A valid handle must never read
 * as an error: on Android 11 to 13 heap pointers carry a tag in their top byte, which makes them negative as a Long
 * (BOAR Labs crashed at first launch on a POCO F3, Android 13, Oct 17).
 */
class NativeSqliteHandleTest {
    @Test fun primaryCodesAreErrors() {
        assertTrue(NativeSqlite.isError(-1))      // SQLITE_ERROR
        assertTrue(NativeSqlite.isError(-5))      // SQLITE_BUSY
        assertTrue(NativeSqlite.isError(-14))     // SQLITE_CANTOPEN
        assertTrue(NativeSqlite.isError(-26))     // SQLITE_NOTADB
    }

    @Test fun extendedCodesAreErrors() {
        assertTrue(NativeSqlite.isError(-266))    // SQLITE_IOERR_READ
        assertTrue(NativeSqlite.isError(-3338))   // SQLITE_IOERR_FSYNC
        assertTrue(NativeSqlite.isError(-1038))   // SQLITE_CANTOPEN_CONVPATH
        assertTrue(NativeSqlite.isError(-0xFFFFF)) // the largest code the check accepts
    }

    @Test fun taggedPointersAreHandles() {
        // Scudo's tag 0xb4 in the top byte, as seen on Android 11-13.
        assertFalse(NativeSqlite.isError(0xb400007a1c2d3e40uL.toLong()))
        assertFalse(NativeSqlite.isError(0xb4000071f2a04010uL.toLong()))
        // Any other tag, and the lowest Long, are pointers, not codes.
        assertFalse(NativeSqlite.isError(0xff00000000000010uL.toLong()))
        assertFalse(NativeSqlite.isError(Long.MIN_VALUE))
    }

    @Test fun plainPointersAreHandles() {
        assertFalse(NativeSqlite.isError(0x0000007a1c2d3e40L))
        assertFalse(NativeSqlite.isError(1))
    }

    @Test fun justPastTheCodesIsNotAnError() {
        assertFalse(NativeSqlite.isError(-0x100000))
    }

    @Test fun zeroIsNotACode() {
        // open treats 0 (no handle) as a failure on its own; it is never an SQLite code.
        assertFalse(NativeSqlite.isError(0))
    }
}
