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

package net.ornithemc.loader.impl.launch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import net.fabricmc.mapping.tree.TinyTree;
import net.ornithemc.api.EnvType;
import net.ornithemc.loader.api.SemanticVersion;
import net.ornithemc.loader.api.Version;
import net.ornithemc.loader.api.VersionParsingException;
import net.ornithemc.loader.api.metadata.ModDependency;
import net.ornithemc.loader.api.metadata.ModDependency.Kind;
import net.ornithemc.loader.api.metadata.version.VersionInterval;
import net.ornithemc.loader.impl.OrnitheLoaderImpl;
import net.ornithemc.loader.impl.ModContainerImpl;
import net.ornithemc.loader.impl.launch.knot.MixinServiceKnot;
import net.ornithemc.loader.impl.launch.knot.MixinServiceKnotBootstrap;
import net.ornithemc.loader.impl.util.log.Log;
import net.ornithemc.loader.impl.util.log.LogCategory;
import net.ornithemc.loader.impl.util.mappings.MixinCalamusDevRemapper;

public final class OrnitheMixinBootstrap {
	private OrnitheMixinBootstrap() { }

	private static boolean initialized = false;

	public static void init(EnvType side, OrnitheLoaderImpl loader) {
		if (initialized) {
			throw new RuntimeException("OrnitheMixinBootstrap has already been initialized!");
		}

		System.setProperty("mixin.bootstrapService", MixinServiceKnotBootstrap.class.getName());
		System.setProperty("mixin.service", MixinServiceKnot.class.getName());

		MixinBootstrap.init();

		if (OrnitheLauncherBase.getLauncher().isDevelopment()) {
			MappingConfiguration mappingConfiguration = OrnitheLauncherBase.getLauncher().getMappingConfiguration();
			TinyTree mappings = mappingConfiguration.getMappings();

			if (mappings != null) {
				List<String> namespaces = mappings.getMetadata().getNamespaces();

				if (namespaces.contains("calamus") && namespaces.contains(mappingConfiguration.getTargetNamespace())) {
					System.setProperty("mixin.env.remapRefMap", "true");

					try {
						MixinCalamusDevRemapper remapper = new MixinCalamusDevRemapper(mappings, "calamus", mappingConfiguration.getTargetNamespace());
						MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
						Log.info(LogCategory.MIXIN, "Loaded Ornithe development mappings for mixin remapper!");
					} catch (Exception e) {
						Log.error(LogCategory.MIXIN, "Ornithe development environment setup error - the game will probably crash soon!");
						e.printStackTrace();
					}
				}
			}
		}

		Map<String, ModContainerImpl> configToModMap = new HashMap<>();

		for (ModContainerImpl mod : loader.getModsInternal()) {
			for (String config : mod.getMetadata().getMixinConfigs(side)) {
				ModContainerImpl prev = configToModMap.putIfAbsent(config, mod);
				if (prev != null) throw new RuntimeException(String.format("Non-unique Mixin config name %s used by the mods %s and %s", config, prev.getMetadata().getId(), mod.getMetadata().getId()));

				try {
					Mixins.addConfiguration(config);
				} catch (Throwable t) {
					throw new RuntimeException(String.format("Error creating Mixin config %s for mod %s", config, mod.getMetadata().getId()), t);
				}
			}
		}

		for (Config config : Mixins.getConfigs()) {
			ModContainerImpl mod = configToModMap.get(config.getName());
			if (mod == null) continue;
		}

		try {
			IMixinConfig.class.getMethod("decorate", String.class, Object.class);
			MixinConfigDecorator.apply(configToModMap);
		} catch (NoSuchMethodException e) {
			Log.info(LogCategory.MIXIN, "Detected old Mixin version without config decoration support");
		}

		initialized = true;
	}

	private static final class MixinConfigDecorator {
		private static final List<LoaderMixinVersionEntry> versions = new ArrayList<>();

		static {
			// maximum loader version and bundled ornithe mixin version, DESCENDING ORDER, LATEST FIRST
			// loader versions with new mixin versions need to be added here

			// addVersion("0.13", FabricUtil.COMPATIBILITY_0_11_0); // example for next entry (latest first!)
			addVersion("0.12.0-", FabricUtil.COMPATIBILITY_0_10_0);
		}

		static void apply(Map<String, ModContainerImpl> configToModMap) {
			for (Config rawConfig : Mixins.getConfigs()) {
				ModContainerImpl mod = configToModMap.get(rawConfig.getName());
				if (mod == null) continue;

				IMixinConfig config = rawConfig.getConfig();
				config.decorate(FabricUtil.KEY_MOD_ID, mod.getMetadata().getId());
				config.decorate(FabricUtil.KEY_COMPATIBILITY, getMixinCompat(mod));
			}
		}

		private static int getMixinCompat(ModContainerImpl mod) {
			// infer from loader dependency by determining the least relevant loader version the mod accepts
			// AND any loader deps

			List<VersionInterval> reqIntervals = Collections.singletonList(VersionInterval.INFINITE);

			for (ModDependency dep : mod.getMetadata().getDependencies()) {
				if (dep.getModId().equals("ornithe-loader")) {
					if (dep.getKind() == Kind.DEPENDS) {
						reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
					} else if (dep.getKind() == Kind.BREAKS) {
						reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
					}
				}
			}

			if (reqIntervals.isEmpty()) throw new IllegalStateException("mod "+mod+" is incompatible with every loader version?"); // shouldn't get there

			Version minLoaderVersion = reqIntervals.get(0).getMin(); // it is sorted, to 0 has the absolute lower bound

			if (minLoaderVersion != null) { // has a lower bound
				for (LoaderMixinVersionEntry version : versions) {
					if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) { // lower bound is >= current version
						return version.mixinVersion;
					} else {
						break;
					}
				}
			}

			return FabricUtil.COMPATIBILITY_0_9_2;
		}

		private static void addVersion(String minLoaderVersion, int mixinCompat) {
			try {
				versions.add(new LoaderMixinVersionEntry(SemanticVersion.parse(minLoaderVersion), mixinCompat));
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
		}

		private static final class LoaderMixinVersionEntry {
			final SemanticVersion loaderVersion;
			final int mixinVersion;

			LoaderMixinVersionEntry(SemanticVersion loaderVersion, int mixinVersion) {
				this.loaderVersion = loaderVersion;
				this.mixinVersion = mixinVersion;
			}
		}
	}
}
