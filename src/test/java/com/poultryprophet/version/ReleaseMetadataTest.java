package com.poultryprophet.version;
import com.poultryprophet.config.ReleaseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import java.util.Properties;
import static org.assertj.core.api.Assertions.assertThat;
class ReleaseMetadataTest {
 @Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(ReleaseProperties.class) static class Config {}
 private BuildProperties build(){var p=new Properties();p.setProperty("time","2026-11-22T00:00:00Z");return new BuildProperties(p);}
 @Test void renderCommitFallbackComesFromTheActualApplicationYaml() throws Exception {
  var env=new MockEnvironment().withProperty("RENDER_GIT_COMMIT","0123456789abcdef0123456789abcdef01234567");
  for(var source:new YamlPropertySourceLoader().load("app",new ClassPathResource("application.yml")))env.getPropertySources().addLast(source);
  assertThat(env.getProperty("app.release.commit-sha")).isEqualTo("0123456789abcdef0123456789abcdef01234567");
 }
 @Test void generatedBuildTimeIsUsedWhenExplicitTimeIsUnknown(){
  new ApplicationContextRunner().withUserConfiguration(Config.class).withBean(BuildProperties.class,this::build)
   .withPropertyValues("app.release.build-time=unknown").run(c->assertThat(c.getBean(ReleaseProperties.class).getBuildTime()).isEqualTo("2026-11-22T00:00:00Z"));
 }
 @Test void explicitBuildTimeStillTakesPrecedence(){
  new ApplicationContextRunner().withUserConfiguration(Config.class).withBean(BuildProperties.class,this::build)
   .withPropertyValues("app.release.build-time=2026-11-22T01:00:00Z").run(c->assertThat(c.getBean(ReleaseProperties.class).getBuildTime()).isEqualTo("2026-11-22T01:00:00Z"));
 }
}
