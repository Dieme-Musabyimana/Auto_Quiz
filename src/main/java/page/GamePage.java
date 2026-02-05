package page;

import com.microsoft.playwright.*;
import java.util.*;

public class GamePage {
    private final Page page;

    // YOUR FINAL LOCATORS
    public final String SPEED_SLIDER = "input#speedSlider";
    public final String DIR_SLIDER   = "input#dirslider";
    public final String START_BTN    = "button#startRoundBtn";
    public final String SHOOT_BTN    = "button#shootBtn";

    public GamePage(Page page) { this.page = page; }

    public int getBestHoleIndex() {
        List<ElementHandle> holes = page.querySelectorAll(".hole");
        int bestIndex = 0;
        int maxReward = 0;
        for (int i = 0; i < holes.size(); i++) {
            String text = holes.get(i).querySelector(".hole-amount-text").innerText();
            int reward = Integer.parseInt(text.replaceAll("[^0-9]", ""));
            if (reward > maxReward) { maxReward = reward; bestIndex = i; }
        }
        return bestIndex;
    }

    public void setSlider(String selector, String value) {
        page.locator(selector).evaluate("el => { " +
                "el.value = '" + value + "'; " +
                "el.dispatchEvent(new Event('input', { bubbles: true })); " +
                "el.dispatchEvent(new Event('change', { bubbles: true })); " +
                "}");
    }
}