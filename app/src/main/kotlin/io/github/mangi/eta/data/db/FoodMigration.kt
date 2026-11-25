package io.github.mangi.eta.data.db

import androidx.room.migration.Migration

internal val FOOD_MIGRATION_26_27 = Migration(26, 27) { db ->
    db.execSQL("""
        CREATE TABLE food_recipes (
            owner TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL,
            json TEXT NOT NULL, cachedAt INTEGER NOT NULL, PRIMARY KEY(owner, id, version)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_recipe_library (
            owner TEXT NOT NULL, recipeId TEXT NOT NULL, json TEXT NOT NULL,
            cachedAt INTEGER NOT NULL, PRIMARY KEY(owner, recipeId)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_recipe_index (
            owner TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, cachedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("""
        CREATE TABLE food_availability (
            owner TEXT NOT NULL, recipeId TEXT NOT NULL, version INTEGER NOT NULL, servings INTEGER NOT NULL,
            json TEXT NOT NULL, stockAsOf TEXT NOT NULL, cachedAt INTEGER NOT NULL,
            PRIMARY KEY(owner, recipeId, version, servings)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_pantry (
            owner TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, fetchedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("""
        CREATE TABLE cooking_sessions (
            id TEXT NOT NULL PRIMARY KEY, owner TEXT, recipeSnapshotJson TEXT NOT NULL,
            servings INTEGER NOT NULL, currentStepId TEXT, completedStepIdsJson TEXT NOT NULL,
            skippedStepIdsJson TEXT NOT NULL, overridesJson TEXT NOT NULL, status TEXT NOT NULL,
            startedAt INTEGER NOT NULL, completedAt INTEGER, stockConsumptionStatus TEXT NOT NULL,
            revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("""
        CREATE TABLE cooking_timers (
            id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, stepId TEXT, label TEXT NOT NULL,
            durationSeconds INTEGER NOT NULL, state TEXT NOT NULL, endsAtEpochMs INTEGER,
            remainingMs INTEGER, createdAt INTEGER NOT NULL,
            FOREIGN KEY(sessionId) REFERENCES cooking_sessions(id) ON DELETE CASCADE
        )
    """)
    db.execSQL("CREATE INDEX index_cooking_timers_sessionId ON cooking_timers(sessionId)")
    db.execSQL("""
        CREATE TABLE food_outbox (
            operationId TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, kind TEXT NOT NULL,
            endpoint TEXT NOT NULL, bodyJson TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL,
            lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("CREATE UNIQUE INDEX index_food_outbox_sessionId ON food_outbox(sessionId)")
    db.execSQL("""
        CREATE TABLE food_brief_cards (
            owner TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, cachedAt INTEGER NOT NULL,
            validUntilEpochMs INTEGER NOT NULL
        )
    """)
}

internal val FOOD_MIGRATION_27_28 = Migration(27, 28) { db ->
    db.execSQL("ALTER TABLE cooking_sessions ADD COLUMN localProposalsJson TEXT")
    db.execSQL("ALTER TABLE cooking_sessions ADD COLUMN briefCardId TEXT")
    db.execSQL("ALTER TABLE food_outbox ADD COLUMN resultJson TEXT")
}

internal val FOOD_MIGRATION_28_29 = Migration(28, 29) { db ->
    db.execSQL("""
        CREATE TABLE food_shopping (
            owner TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, fetchedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("""
        CREATE TABLE food_meal_weeks (
            owner TEXT NOT NULL, weekStart TEXT NOT NULL, json TEXT NOT NULL, fetchedAt INTEGER NOT NULL,
            PRIMARY KEY(owner, weekStart)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_brief_cards_v2 (
            owner TEXT NOT NULL, kinds TEXT NOT NULL, json TEXT NOT NULL, cachedAt INTEGER NOT NULL,
            validUntilEpochMs INTEGER NOT NULL, PRIMARY KEY(owner, kinds)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_cuisine_operations (
            operationId TEXT NOT NULL, owner TEXT NOT NULL, kind TEXT NOT NULL, target TEXT,
            bodyJson TEXT NOT NULL, projectionJson TEXT, state TEXT NOT NULL, attempts INTEGER NOT NULL,
            resultJson TEXT, lastError TEXT, awaitingRefresh INTEGER NOT NULL,
            sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
        )
    """)
    db.execSQL("CREATE UNIQUE INDEX index_food_cuisine_operations_operationId ON food_cuisine_operations(operationId)")
    db.execSQL("CREATE INDEX index_food_cuisine_operations_owner_sequence ON food_cuisine_operations(owner, sequence)")
}
