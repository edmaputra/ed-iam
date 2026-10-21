package io.github.edmaputra.iam.playground.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.github.edmaputra.iam.playground.PlaygroundApplication;
import io.github.edmaputra.iam.playground.seeder.PlaygroundDataSeeder;

/**
 * Controller serving the interactive Thymeleaf dashboard.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
public class PlaygroundViewController {

	private final String appVersion;

	public PlaygroundViewController(
			@Autowired(required = false) BuildProperties buildProperties,
			@Value("${playground.version:@project.version@}") String fallbackVersion) {
		if (buildProperties != null && buildProperties.getVersion() != null) {
			this.appVersion = buildProperties.getVersion();
		} else if (fallbackVersion != null && !fallbackVersion.startsWith("@")) {
			this.appVersion = fallbackVersion;
		} else {
			String pkgVersion = PlaygroundApplication.class.getPackage().getImplementationVersion();
			this.appVersion = (pkgVersion != null && !pkgVersion.isBlank()) ? pkgVersion : "0.14.0-SNAPSHOT";
		}
	}

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("appVersion", appVersion);
		model.addAttribute("metroTenantId", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID);
		model.addAttribute("stJudeTenantId", PlaygroundDataSeeder.ST_JUDE_TENANT_ID);
		model.addAttribute("rootScopeId", PlaygroundDataSeeder.METRO_ROOT_SCOPE_ID);
		model.addAttribute("cardiologyScopeId", PlaygroundDataSeeder.CARDIOLOGY_SCOPE_ID);
		model.addAttribute("icuScopeId", PlaygroundDataSeeder.ICU_SCOPE_ID);
		model.addAttribute("pediatricsScopeId", PlaygroundDataSeeder.PEDIATRICS_SCOPE_ID);
		model.addAttribute("defaultPassword", PlaygroundDataSeeder.DEMO_PASSWORD);
		model.addAttribute("nurseEmail", PlaygroundDataSeeder.NURSE_EMAIL);
		model.addAttribute("suspendedEmail", PlaygroundDataSeeder.SUSPENDED_EMAIL);
		model.addAttribute("surgicalGroupCode", PlaygroundDataSeeder.SURGICAL_GROUP_CODE);
		return "index";
	}
}
