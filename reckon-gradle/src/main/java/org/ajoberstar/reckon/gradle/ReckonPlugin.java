package org.ajoberstar.reckon.gradle;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.ajoberstar.grgit.Grgit;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class ReckonPlugin implements Plugin<Project> {
  private static final String TAG_TASK = "reckonTagCreate";
  private static final String PUSH_TASK = "reckonTagPush";

  @Override
  public void apply(Project project) {
    if (!project.equals(project.getRootProject())) {
      throw new IllegalStateException("org.ajoberstar.reckon can only be applied to the root project.");
    }
    project.getPluginManager().apply("org.ajoberstar.grgit");

    Grgit grgit = (Grgit) project.findProperty("grgit");
    ReckonExtension extension = project.getExtensions().create("reckon", ReckonExtension.class, project, grgit);

    DelayedVersion sharedVersion = new DelayedVersion(extension::reckonVersion);
    project.allprojects(prj -> {
      prj.setVersion(sharedVersion);
    });

    Task tag = createTagTask(project, extension, grgit);
    Task push = createPushTask(project, extension, grgit, tag);
    push.dependsOn(tag);
  }

  private Task createTagTask(Project project, ReckonExtension extension, Grgit grgit) {
    Task task = project.getTasks().create(TAG_TASK);
    task.setDescription("Tag version inferred by reckon.");
    task.setGroup("publishing");
    task.onlyIf(t -> {
      String version = project.getVersion().toString();
      // using the presence of build metadata as the indicator of taggable versions
      boolean insignificant = version.contains("+");
      // rebuilds shouldn't trigger a new tag
      boolean alreadyTagged = grgit.getTag().list().stream()
          .anyMatch(tag -> tag.getName().equals(version));
      return !(insignificant || alreadyTagged);
    });
    task.doLast(t -> {
      Map<String, Object> args = new HashMap<>();
      args.put("name", project.getVersion());
      args.put("message", project.getVersion());
      grgit.getTag().add(args);
    });
    return task;
  }

  private Task createPushTask(Project project, ReckonExtension extension, Grgit grgit, Task create) {
    Task task = project.getTasks().create(PUSH_TASK);
    task.setDescription("Push version tag created by reckon.");
    task.setGroup("publishing");
    task.onlyIf(t -> create.getDidWork());
    task.doLast(t -> {
      Map<String, Object> args = new HashMap<>();
      args.put("refsOrSpecs", Arrays.asList("refs/tags/" + project.getVersion().toString()));
      grgit.push(args);
    });
    return task;
  }

  private static class DelayedVersion {
    private final Supplier<String> reckoner;

    public DelayedVersion(Supplier<String> reckoner) {
      this.reckoner = Suppliers.memoize(reckoner);
    }

    @Override
    public String toString() {
      return reckoner.get();
    }
  }
}
