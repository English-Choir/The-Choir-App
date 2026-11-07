package com.phantominnovation.choir

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface RegistrationLibrary {
    fun observe(): Flow<LibraryState>
    suspend fun add(input: RegistrationInput): Catalog
    suspend fun addSection(title: String): Catalog
    suspend fun changeSection(section: ChoirSection, title: String, moveTo: String? = null): Catalog
    suspend fun update(id: String, input: RegistrationInput, expected: Registration? = null): Catalog
    suspend fun delete(id: String, expected: Registration? = null): Catalog
    suspend fun saveLinks(id: String, links: List<DriveLink>, expected: Registration? = null): Catalog
}

/** Keeps the existing local data reader and isolated UI tests usable without a cloud account. */
class LocalRegistrationLibrary(private val store: RegistrationStore) : RegistrationLibrary {
    override fun observe() = flow { emit(LibraryState(store.load())) }
    override suspend fun add(input: RegistrationInput) = store.add(input)
    override suspend fun addSection(title: String) = store.addSection(title)
    override suspend fun changeSection(section: ChoirSection, title: String, moveTo: String?) = store.changeSection(section, title, moveTo)
    override suspend fun update(id: String, input: RegistrationInput, expected: Registration?) = store.update(id, input)
    override suspend fun delete(id: String, expected: Registration?) = store.delete(id)
    override suspend fun saveLinks(id: String, links: List<DriveLink>, expected: Registration?) = store.saveLinks(id, links)
}
