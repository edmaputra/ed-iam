package io.github.edmaputra.iam.playground.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.github.edmaputra.iam.playground.seeder.PlaygroundDataSeeder;

/**
 * Controller serving the interactive Thymeleaf dashboard.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
public class PlaygroundViewController {

	@GetMapping("/")
	public String index(Model model) {
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
