/*
 * Copyright 2012 - 2016 Manuel Laggner
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
package org.tinymediamanager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.CertificationStyle;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.MovieList;
import org.tinymediamanager.core.movie.MovieSetArtworkHelper;
import org.tinymediamanager.core.movie.connector.MovieConnectors;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.entities.MovieActor;
import org.tinymediamanager.core.movie.entities.MovieSet;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.util.StrgUtils;

import com.sun.jna.Platform;

/**
 * The class UpdateTasks. To perform needed update tasks
 * 
 * @author Manuel Laggner / Myron Boyle
 */
public class UpgradeTasks {
  private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeTasks.class);

  public static void performUpgradeTasksBeforeDatabaseLoading(String oldVersion) {
    String v = "" + oldVersion;
    if (StringUtils.isBlank(v)) {
      v = "2.6.9"; // set version for other updates
    }

    // ****************************************************
    // PLEASE MAKE THIS TO RUN MULTIPLE TIMES WITHOUT ERROR
    // NEEDED FOR NIGHTLY SNAPSHOTS ET ALL
    // SVN BUILD IS ALSO CONSIDERED AS LOWER !!!
    // ****************************************************

    // upgrade to v2.7 (OR DO THIS IF WE ARE INSIDE IDE)
    if (StrgUtils.compareVersion(v, "2.7") < 0) {
      LOGGER.info("Performing upgrade tasks to version 2.7");
      // migrate to config dir
      moveToConfigFolder(Paths.get("movies.db"));
      moveToConfigFolder(Paths.get("tvshows.db"));
      moveToConfigFolder(Paths.get("scraper_imdb.conf"));
      moveToConfigFolder(Paths.get("tmm_ui.prop"));

      // cleaup of native folder
      cleanupNativeFolder();
    }

    // upgrade to v2.7.2
    if (StrgUtils.compareVersion(v, "2.7.2") < 0) {
      LOGGER.info("Performing upgrade tasks to version 2.7.2");
      // delete all linux-* files in native
      if (Platform.isLinux()) {
        File[] subdirs = new File("native").listFiles();
        if (subdirs != null) {
          for (File subdir : subdirs) {
            if (subdir.isDirectory() && subdir.getName().startsWith("linux")) {
              FileUtils.deleteQuietly(subdir);
            }
          }
        }
      }
    }
  }

  private static void moveToConfigFolder(Path file) {
    if (Files.exists(file)) {
      Path fnew = Paths.get(Settings.getInstance().getSettingsFolder(), file.getFileName().toString());
      try {
        Utils.moveFileSafe(file, fnew);
      }
      catch (IOException e) {
        LOGGER.warn("error moving " + file);
      }
    }
  }

  /**
   * performs some upgrade tasks from one version to another<br>
   * <b>make sure, this upgrade can run multiple times (= needed for nightlies!!!)
   * 
   * @param oldVersion
   *          our current version
   */
  public static void performUpgradeTasksAfterDatabaseLoading(String oldVersion) {
    MovieList movieList = MovieList.getInstance();
    TvShowList tvShowList = TvShowList.getInstance();

    String v = "" + oldVersion;

    if (StringUtils.isBlank(v)) {
      v = "2.6.9"; // set version for other updates
    }

    // ****************************************************
    // PLEASE MAKE THIS TO RUN MULTIPLE TIMES WITHOUT ERROR
    // NEEDED FOR NIGHTLY SNAPSHOTS ET ALL
    // SVN BUILD IS ALSO CONSIDERED AS LOWER !!!
    // ****************************************************

    // upgrade to v2.7
    if (StrgUtils.compareVersion(v, "2.7") < 0) {
      LOGGER.info("Performing database upgrade tasks to version 2.7");
      // delete tmm.odb; objectdb.conf; log dir
      FileUtils.deleteQuietly(new File("tmm.odb"));
      FileUtils.deleteQuietly(new File("tmm.odb$"));
      FileUtils.deleteQuietly(new File("objectdb.conf"));
      FileUtils.deleteQuietly(new File("log"));
      Globals.settings.removeSubtitleFileType(".idx"); // aww, we never removed...

      // We do not migrate settings!
      // We cannot determine, if a user has unset a value, or the default changed!
      // So reSet some default values, but ONLY for release ONCE;
      // else every start of prerel/nightly would reset this over and over again
      if (ReleaseInfo.isReleaseBuild()) {
        Globals.settings.getMovieSettings().setImageBanner(true);
        Globals.settings.getMovieSettings().setImageLogo(true);
        Globals.settings.getMovieSettings().setImageClearart(true);
        Globals.settings.getMovieSettings().setImageDiscart(true);
        Globals.settings.getMovieSettings().setImageThumb(true);
        Globals.settings.getMovieSettings().setUseTrailerPreference(true);
        Globals.settings.writeDefaultSettings(); // activate default plugins
      }
    }

    // upgrade to v2.7.2
    if (StrgUtils.compareVersion(v, "2.7.2") < 0) {
      LOGGER.info("Performing database upgrade tasks to version 2.7.2");
      // we forgot to update the actor thumbs in DB
      for (Movie movie : movieList.getMovies()) {
        boolean dirty = false;
        for (MovieActor actor : movie.getActors()) {
          if (StringUtils.isNotBlank(actor.getThumbPath())) {
            if (actor.updateThumbRoot(movie.getPath())) {
              // true when changed
              dirty = true;
            }
          }
        }
        if (dirty) {
          movie.saveToDb();
        }
      }
    }

    // upgrade to v2.7.3
    if (StrgUtils.compareVersion(v, "2.7.3") < 0) {
      LOGGER.info("Performing database upgrade tasks to version 2.7.3");
      // get movie set artwork
      for (MovieSet movieSet : movieList.getMovieSetList()) {
        MovieSetArtworkHelper.updateArtwork(movieSet);
        movieSet.saveToDb();
      }

      // reset new indicator
      for (Movie movie : movieList.getMovies()) {
        movie.setNewlyAdded(false);
        movie.saveToDb();
      }
      for (TvShow tvShow : tvShowList.getTvShows()) {
        for (TvShowEpisode episode : tvShow.getEpisodes()) {
          episode.setNewlyAdded(false);
          episode.saveToDb();
        }
        tvShow.saveToDb();
      }
    }

    // upgrade to v2.8
    if (StrgUtils.compareVersion(v, "2.8") < 0) {
      LOGGER.info("Performing database upgrade tasks to version 2.8");

      // upgrade certification settings
      // if MP NFO style is chosen, set the certification style to TECHNICAL
      if (Globals.settings.getMovieSettings().getMovieConnector() == MovieConnectors.MP) {
        Globals.settings.getMovieSettings().setMovieCertificationStyle(CertificationStyle.TECHNICAL);
      }

      // reevaluate movie stacking and offline stubs (without the need for UDS) and save
      for (Movie movie : movieList.getMovies()) {
        movie.reEvaluateStacking();
        boolean isOffline = false;
        for (MediaFile mf : movie.getMediaFiles(MediaFileType.VIDEO)) {
          if ("disc".equalsIgnoreCase(mf.getExtension())) {
            isOffline = true;
          }
        }
        movie.setOffline(isOffline);
        movie.saveToDb();
      }
    }
    // upgrade to v2.8.2
    if (StrgUtils.compareVersion(v, "2.8.2") < 0) {
      LOGGER.info("Performing database upgrade tasks to version 2.8.2");

      Date initialDate = new Date(0);

      for (Movie movie : movieList.getMovies()) {
        if (movie.getReleaseDate() != null && DateUtils.isSameDay(initialDate, movie.getReleaseDate())) {
          movie.setReleaseDate((Date) null);
          movie.saveToDb();
        }
      }

      for (TvShow tvShow : tvShowList.getTvShows()) {
        if (tvShow.getFirstAired() != null && DateUtils.isSameDay(initialDate, tvShow.getFirstAired())) {
          tvShow.setFirstAired((Date) null);
          tvShow.saveToDb();
        }
        for (TvShowEpisode episode : tvShow.getEpisodes()) {
          if (episode.getFirstAired() != null && DateUtils.isSameDay(initialDate, episode.getFirstAired())) {
            episode.setFirstAired((Date) null);
            episode.saveToDb();
          }
        }
      }
    }

    // upgrade to v2.8.3
    if (StrgUtils.compareVersion(v, "2.8.3") < 0) {
      LOGGER.info("Performing database upgrade tasks to version 2.8.3");

      // reset "container format" for MFs, so that MI tries them again on next UDS (ISOs and others)
      // (but only if we do not have some video information yet, like "width")
      for (Movie movie : movieList.getMovies()) {
        boolean changed = false;
        for (MediaFile mf : movie.getMediaFiles(MediaFileType.VIDEO)) {
          if (mf.getVideoResolution().isEmpty()) {
            mf.setContainerFormat("");
            changed = true;
          }
        }
        if (changed) {
          movie.saveToDb();
        }
      }
      for (TvShow tvShow : tvShowList.getTvShows()) {
        for (TvShowEpisode episode : tvShow.getEpisodes()) {
          boolean changed = false;
          for (MediaFile mf : episode.getMediaFiles(MediaFileType.VIDEO)) {
            if (mf.getVideoResolution().isEmpty()) {
              mf.setContainerFormat("");
              changed = true;
            }
          }
          if (episode.isDisc()) {
            // correct episode path when extracted disc folder
            Path discRoot = episode.getPathNIO().toAbsolutePath(); // folder
            String folder = tvShow.getPathNIO().relativize(discRoot).toString().toUpperCase(); // relative
            while (folder.contains("BDMV") || folder.contains("VIDEO_TS")) {
              discRoot = discRoot.getParent();
              folder = tvShow.getPathNIO().relativize(discRoot).toString().toUpperCase(); // reevaluate
              episode.setPath(discRoot.toAbsolutePath().toString());
              changed = true;
            }
          }
          if (changed) {
            episode.saveToDb();
          }
        }
      }
    }

  }

  /**
   * cleanup the native folder
   * 
   * only the specified folders should survive
   * 
   * Windows: windows-x86 windows-x64 Linux: linux-x86 linux-x64 Mac OSX: mac-x86 mac-x64
   */
  private static void cleanupNativeFolder() {
    // no cleanup in SVN
    if (ReleaseInfo.isSvnBuild()) {
      return;
    }

    try {
      File[] nativeFiles = new File("native").listFiles();
      if (nativeFiles == null) {
        return;
      }

      for (File file : nativeFiles) {
        if (!file.isDirectory()) {
          continue;
        }

        if (Platform.isWindows() && !"windows-x86".equals(file.getName()) && !"windows-x64".equals(file.getName())) {
          FileUtils.deleteQuietly(file);
        }
        else if (Platform.isLinux() && !"linux-x86".equals(file.getName()) && !"linux-x64".equals(file.getName())) {
          FileUtils.deleteQuietly(file);
        }
        else if (Platform.isMac() && !"mac-x86".equals(file.getName()) && !"mac-x64".equals(file.getName())) {
          FileUtils.deleteQuietly(file);
        }
      }
    }
    catch (Exception e) {
      LOGGER.warn("failed to cleanup native folder: " + e.getMessage());
    }
  }

  /**
   * rename downloaded files (getdown.jar, ...)
   */
  public static void renameDownloadedFiles() {
    // self updater
    File file = new File("getdown-new.jar");
    if (file.exists() && file.length() > 100000) {
      File cur = new File("getdown.jar");
      if (file.length() != cur.length() || !cur.exists()) {
        try {
          FileUtils.copyFile(file, cur);
        }
        catch (IOException e) {
          LOGGER.error("Could not update the updater!");
        }
      }
    }

    // exe launchers
    if (Platform.isWindows()) {
      file = new File("tinyMediaManager.new");
      if (file.exists() && file.length() > 10000 && file.length() < 50000) {
        File cur = new File("tinyMediaManager.exe");
        try {
          FileUtils.copyFile(file, cur);
        }
        catch (IOException e) {
          LOGGER.error("Could not update tmm!");
        }
      }
      file = new File("tinyMediaManagerUpd.new");
      if (file.exists() && file.length() > 10000 && file.length() < 50000) {
        File cur = new File("tinyMediaManagerUpd.exe");
        try {
          FileUtils.copyFile(file, cur);
        }
        catch (IOException e) {
          LOGGER.error("Could not update the updater!");
        }
      }
      file = new File("tinyMediaManagerCMD.new");
      if (file.exists() && file.length() > 10000 && file.length() < 50000) {
        File cur = new File("tinyMediaManagerCMD.exe");
        try {
          FileUtils.copyFile(file, cur);
        }
        catch (IOException e) {
          LOGGER.error("Could not update CMD TMM!");
        }
      }
    }

    // OSX launcher
    if (Platform.isMac()) {
      file = new File("JavaApplicationStub.new");
      if (file.exists() && file.length() > 0) {
        File cur = new File("../../MacOS/JavaApplicationStub");
        try {
          FileUtils.copyFile(file, cur);
        }
        catch (IOException e) {
          LOGGER.error("Could not update JavaApplicationStub");
        }
      }
    }

    // OSX Info.plist
    if (Platform.isMac()) {
      file = new File("Info.plist");
      if (file.exists() && file.length() > 0) {
        File cur = new File("../../Info.plist");
        try {
          FileUtils.copyFile(file, cur);
        }
        catch (IOException e) {
          LOGGER.error("Could not update JavaApplicationStub");
        }
      }
    }
  }
}
