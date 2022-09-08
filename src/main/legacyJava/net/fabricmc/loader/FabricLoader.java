/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader;

import java.io.File;
import java.util.Collection;
import java.util.List;

import net.ornithemc.loader.impl.OrnitheLoaderImpl;

/**
 * The main class for mod loading operations.
 *
 * @deprecated Use {@link net.ornithemc.loader.api.OrnitheLoader}
 */
@Deprecated
public abstract class FabricLoader implements net.ornithemc.loader.api.OrnitheLoader {
	/**
	 * @deprecated Use {@link net.ornithemc.loader.api.OrnitheLoader#getInstance()} where possible,
	 * report missing areas as an issue.
	 */
	@Deprecated
	public static final FabricLoader INSTANCE = OrnitheLoaderImpl.InitHelper.get();

	public File getModsDirectory() {
		return getGameDir().resolve("mods").toFile();
	}

	@Override
	public abstract <T> List<T> getEntrypoints(String key, Class<T> type);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Collection<ModContainer> getModContainers() {
		return (Collection) getAllMods();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<ModContainer> getMods() {
		return (List) getAllMods();
	}
}
