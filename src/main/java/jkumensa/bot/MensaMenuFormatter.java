package jkumensa.bot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import jkumensa.api.MensaCategory;
import jkumensa.api.MensaFoodCharacteristic;
import jkumensa.api.MensaMeal;
import jkumensa.api.Priced;

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

    public String getCategory(MensaCategory cat) {
        StringBuilder sb = new StringBuilder();

        sb.append("*~~~~~ ").append(cat.getTitle());
        String catFoodCharacteristics = generateFoodCharacteristicsString(cat.getFoodCharacteristics());
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

        for (MensaMeal meal : cat.getMeals()) {
            appendMeal(sb, meal);
        }

        return sb.toString();
    }

    private void appendMeal(StringBuilder sb, MensaMeal meal) {
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

        String allergyString = meal.getAllergyCodes().stream()
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();

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

    private String generateFoodCharacteristicsString(Set<? extends MensaFoodCharacteristic> fcs) {
        StringBuilder sb = new StringBuilder();
        for (MensaFoodCharacteristic fc : fcs) {
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
