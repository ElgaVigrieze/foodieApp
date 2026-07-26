package com.foodie.controller;

import com.foodie.service.CurrentUserService;
import com.foodie.service.HouseholdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/household")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public String show(Model model) {
        var user = currentUserService.getCurrentUser();
        if (user != null && user.getHousehold() != null) {
            model.addAttribute("household", user.getHousehold());
            model.addAttribute("isOwner", user.isOwner());
            return "household/view";
        }
        return "household/setup";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name, RedirectAttributes ra) {
        householdService.createHousehold(name);
        ra.addFlashAttribute("success", "Household created!");
        return "redirect:/household";
    }

    @PostMapping("/join")
    public String join(@RequestParam String inviteCode, RedirectAttributes ra) {
        if (householdService.joinHousehold(inviteCode)) {
            ra.addFlashAttribute("success", "Joined household!");
        } else {
            ra.addFlashAttribute("error", "Invalid invite code.");
        }
        return "redirect:/household";
    }
}
