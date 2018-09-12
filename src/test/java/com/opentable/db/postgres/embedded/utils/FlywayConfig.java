/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.db.postgres.embedded.utils;

import org.flywaydb.core.Flyway;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

public final class FlywayConfig {

  private Flyway flyway;

  private static FlywayConfig parseFlywayProperties() {
    Representer representer = new Representer();
    representer.getPropertyUtils().setSkipMissingProperties(true);
    Yaml yaml = new Yaml(new Constructor(FlywayConfig.class), representer);
    return yaml.load(FlywayConfig.class.getResourceAsStream("/application.yml"));
  }

  public static Flyway get() {
    return parseFlywayProperties().getFlyway();
  }

  private Flyway getFlyway() {
    return flyway;
  }

  public void setFlyway(Flyway flyway) {
    this.flyway = flyway;
  }

}
