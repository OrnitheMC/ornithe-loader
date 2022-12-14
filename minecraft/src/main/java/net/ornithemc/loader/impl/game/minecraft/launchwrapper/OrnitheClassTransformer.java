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

package net.ornithemc.loader.impl.game.minecraft.launchwrapper;

import net.minecraft.launchwrapper.IClassTransformer;

import net.ornithemc.api.EnvType;
import net.ornithemc.loader.impl.OrnitheLoaderImpl;
import net.ornithemc.loader.impl.launch.OrnitheLauncherBase;
import net.ornithemc.loader.impl.transformer.OrnitheTransformer;

public class OrnitheClassTransformer implements IClassTransformer {
	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		boolean isDevelopment = OrnitheLauncherBase.getLauncher().isDevelopment();
		EnvType envType = OrnitheLauncherBase.getLauncher().getEnvironmentType();

		byte[] input = OrnitheLoaderImpl.INSTANCE.getGameProvider().getEntrypointTransformer().transform(name);

		if (input != null) {
			return OrnitheTransformer.transform(isDevelopment, envType, name, input);
		} else {
			if (bytes != null) {
				return OrnitheTransformer.transform(isDevelopment, envType, name, bytes);
			} else {
				return null;
			}
		}
	}
}
