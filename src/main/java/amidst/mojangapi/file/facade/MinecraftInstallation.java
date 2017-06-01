package amidst.mojangapi.file.facade;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import amidst.documentation.Immutable;
import amidst.logging.AmidstLogger;
import amidst.mojangapi.file.DotMinecraftDirectoryNotFoundException;
import amidst.mojangapi.file.MojangApiParsingException;
import amidst.mojangapi.file.directory.DotMinecraftDirectory;
import amidst.mojangapi.file.directory.SaveDirectory;
import amidst.mojangapi.file.directory.VersionDirectory;
import amidst.mojangapi.file.json.version.VersionJson;
import amidst.mojangapi.file.service.DotMinecraftDirectoryService;
import amidst.mojangapi.file.service.SaveDirectoryService;
import amidst.parsing.FormatException;
import amidst.parsing.json.JsonReader;

@Immutable
public class MinecraftInstallation {
	public static MinecraftInstallation newCustomMinecraftInstallation(
			File libraries,
			File saves,
			File versions,
			File launcherProfilesJson) throws DotMinecraftDirectoryNotFoundException {
		DotMinecraftDirectory dotMinecraftDirectory = new DotMinecraftDirectoryService()
				.createCustomDotMinecraftDirectory(libraries, saves, versions, launcherProfilesJson);
		AmidstLogger.info("using '.minecraft' directory at: '" + dotMinecraftDirectory.getRoot() + "'");
		return new MinecraftInstallation(dotMinecraftDirectory);
	}

	public static MinecraftInstallation newLocalMinecraftInstallation(String preferredDotMinecraftDirectory)
			throws DotMinecraftDirectoryNotFoundException {
		DotMinecraftDirectory dotMinecraftDirectory = new DotMinecraftDirectoryService()
				.createDotMinecraftDirectory(preferredDotMinecraftDirectory);
		AmidstLogger.info("using '.minecraft' directory at: '" + dotMinecraftDirectory.getRoot() + "'");
		return new MinecraftInstallation(dotMinecraftDirectory);
	}

	private final DotMinecraftDirectoryService dotMinecraftDirectoryService = new DotMinecraftDirectoryService();
	private final DotMinecraftDirectory dotMinecraftDirectory;

	public MinecraftInstallation(DotMinecraftDirectory dotMinecraftDirectory) {
		this.dotMinecraftDirectory = dotMinecraftDirectory;
	}

	public List<UnresolvedLauncherProfile> readLauncherProfiles() throws MojangApiParsingException, IOException {
		return dotMinecraftDirectoryService
				.readLauncherProfilesFrom(dotMinecraftDirectory)
				.getProfiles()
				.values()
				.stream()
				.map(p -> new UnresolvedLauncherProfile(dotMinecraftDirectory, p))
				.collect(Collectors.toList());
	}

	public LauncherProfile newLauncherProfile(String versionId) throws MojangApiParsingException, IOException {
		return newLauncherProfile(
				dotMinecraftDirectoryService.createValidVersionDirectory(dotMinecraftDirectory, versionId));
	}

	public LauncherProfile newLauncherProfile(File jar, File json) throws MojangApiParsingException, IOException {
		return newLauncherProfile(dotMinecraftDirectoryService.createValidVersionDirectory(jar, json));
	}

	private LauncherProfile newLauncherProfile(VersionDirectory versionDirectory)
			throws IOException,
			MojangApiParsingException {
		try {
			VersionJson versionJson = JsonReader.readLocation(versionDirectory.getJson(), VersionJson.class);
			return new LauncherProfile(
					dotMinecraftDirectory,
					dotMinecraftDirectory.asProfileDirectory(),
					versionDirectory,
					versionJson,
					versionJson.getId());
		} catch (FormatException e) {
			throw new MojangApiParsingException(e);
		}
	}

	public SaveGame newSaveGame(File location) throws IOException, MojangApiParsingException {
		SaveDirectoryService saveDirectoryService = new SaveDirectoryService();
		SaveDirectory saveDirectory = saveDirectoryService.newSaveDirectory(location);
		return new SaveGame(saveDirectory, saveDirectoryService.createLevelDat(saveDirectory));
	}
}
