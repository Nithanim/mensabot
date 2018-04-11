/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jkumensa.bot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import jkumensa.parser.i.Category;
import jkumensa.parser.i.FoodCharacteristic;
import jkumensa.parser.i.Meal;
import jkumensa.parser.i.Priced;

public class MensaMenuFormatter {
    private static final DateTimeFormatter printFormat = DateTimeFormatter.ofPattern("EE dd.MM.yyyy", Locale.GERMAN);

    public String getInternalError(MensaBot mb) {
        return "\u26a1 \u26a1 \u26a1 `Internal Error, sorry` \u26a1 \u26a1 \u26a1 \n"
            + "Visit the sites directly instead: \n"
            + mb.getMensaLinks();
    }

    public String getUnavalilableText(MensaBot mb) {
        return "\u26a1 \u26a1 \u26a1 `No data available, sorry` \u26a1 \u26a1 \u26a1 \n"
            + "Visit the sites directly instead: \n"
            + mb.getMensaLinks();
    }

    public String getSplitter() {
        return "\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\n"
            + "\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\n"
            + "\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\n";
    }

    public String getMensaTitle(String title, LocalDate date) {
        return "```" + "\n##### " + title + " #####\n" + printFormat.format(date) + "```";
    }

    public String getCategory(Category cat) {
        StringBuilder sb = new StringBuilder();

        sb.append("*~~~~~ ").append(cat.getTitle());
        String catFoodCharacteristics = generateFoodCharacteristicsString(cat.getAttachments());
        if (!catFoodCharacteristics.isEmpty()) {
            sb.append("  ");
            sb.append(catFoodCharacteristics);
        }
        sb.append(" ~~~~~*");

        String catPrice = generatePriceString(cat);
        if (!catPrice.isEmpty()) {
            sb.append("   ").append(catPrice);
        }

        sb.append('\n');

        for (Meal meal : cat.getMeals()) {
            appendMeal(sb, meal);
        }

        return sb.toString();
    }

    private void appendMeal(StringBuilder sb, Meal meal) {
        sb.append(meal.getTitle());

        String mealPrice = generatePriceString(meal);
        if (!mealPrice.isEmpty()) {
            sb.append(" ").append(mealPrice).append("  ");
        }

        String mealFoodCharacteristics = generateFoodCharacteristicsString(meal.getFoodCharacteristics());
        if (!mealFoodCharacteristics.isEmpty()) {
            sb.append(mealFoodCharacteristics);
            sb.append("   ");
        }

        String allergyString = meal.getAllergyCodes().stream().map(c -> c.name()).collect(Collectors.joining(", "));
        if (!allergyString.isEmpty()) {
            sb.append(" (");
            sb.append(allergyString);
            sb.append(')');
        }
        sb.append('\n');
    }

    private String generatePriceString(Priced priced) {
        StringBuilder sb = new StringBuilder();
        if (priced.getPriceStudent() > 0 || priced.getPriceStudentBonus() > 0) { //assume discount
            sb.append(priced.getPriceGuest()).append('/');
            sb.append(priced.getPriceStudent()).append('/');
            sb.append(priced.getPriceStudentBonus()).append('€');
            return sb.toString();
        } else if (priced.getPriceGuest() > 0) {
            sb.append(priced.getPriceGuest()).append('€');
            return sb.toString();
        } else {
            return "";
        }
    }

    private String generateFoodCharacteristicsString(Set<? extends FoodCharacteristic> fcs) {
        StringBuilder sb = new StringBuilder();
        for (FoodCharacteristic fc : fcs) {
            switch (fc) {
                case VEGAN:
                    //sb.append("\ud83c\udf33");
                    sb.append("\ud83c\udf3b ");
                    break;
                case FISH:
                    sb.append("\ud83d\udc1f ");
                    break;
                case VEGETARIAN:
                    sb.append("\ud83c\uDF31 ");
                    break;
                case BRAINFOOD:
                    sb.append("\ud83d\udca1 ");
                    break;
                case MSC:
                    sb.append("\ud83c\udf0a ");
                    break;
            }
        }
        return sb.toString();
    }
}
