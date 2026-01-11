package com.agty;

import com.agty.urlextractor.URLRedirectResolver;

/**
 * Test redirect resolution for Cadremploi URLs
 */
public class TestCadreMploiRedirectResolution {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         Cadremploi Redirect Resolution Test                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Test URLs from the email
        String[] testUrls = {
            "https://r.emails3.alertes.cadremploi.fr/tr/cl/xoRgpmb1Wjp82OCZsMFE656ULgze6xBWsZrqikS0IQuSpuqNDGQw7IMARVimcYAOmZVleaF2RvPGEmDmQ_h4c7fX0ThhW-gGJZV0bzxDqrqlUVlgLhcAzuUwSrNcq0WXVYj-aOvEFHEDFrdypBhZBsKNm_WZ6117hHA93sMQ59_IiC2l5XqrBxcqniufe8uwry4zIEhfWDjZjo9rsQJZctYwGLl1Mv4R_S7rMV6KtfmHs0wcqpRDy02RgFkW_oAC-LvXbC-5yBWO3HUHXb6MeVUSlYoL2jw4wS3tLodUlmMeOtaElwsEtynL5FIsx89HHeN7B9E55ITvJZubtApxSMEefD4sCqMdYpseiSuMPZSCSswVWJpoBr8JPq1YTAz4__SUAj0PVsClKn1NwcJyrqVqPxbubBlTGoq-MvcSje_kasZa_O6lJy_YHrO-QBafG-UCo9w51gHItUhB7N24n-Mf5LxBOkJXsNGDVepbtDwRzz3dfQLT47Wu3TCeQ6OJMTbFfXUAvxjyI-N3Sb0iryAKdyd375ay4sKSlKt1cnXmjN1Egft0VSQSNWCcR2P8ByIH1fARJB3TA98IqTWb61waB3dFT6ChuR8qliUlzOzK-PTD7LBsfV5IgmlQ0k3VFiHAme5y5gP34KyWkjXCPEqt7tY73O28JKFoyD09zT-wjOQRgEiE0sm1CHL4-L3pRT8VW1ojJTLrcfeKchvsFHKSZr0CoyB--UYTTmF2pd_U2pukr-ByoMlIpUgzhe31B-OGuwk1n8xZmLU2pBqn5oPktHYnKEa_pkHdNiUeUbV5tNzgvG4M3pPS0RNBDnnf25rT2Vg7R7mcjTDb5JfhBXrAqt-q",
            "https://r.emails3.alertes.cadremploi.fr/tr/cl/Vjaub9DV4H-VqKfnRhKrPCwwJlImm7zM8WjmPQBUVyfwqSkXKNNGorAhXJ6-Sznk5nWMcqPyV33ljCC3Cw3J3cG3W5_-noBLUFlTMnkbB0b60eCKHHnpwH65KSLpqUoDG0RUtFiay2zYVa7I2XQWaiUrupaJQdBBFVU_eca1hdPHlWeQors3ZZ605eac10gn9PzzgoiGFGoRD2xAgsVVZeQ68mwegHFVoQEGm8glfU6FJSg29kUfX5hnaQrp7nS_WcP2jQGTCexZIKmoJDJhF65K40ldhU1Zpg9Gjtgy9ZQn6qs4aBrSg0zk7sAwOR5P2JQyckardvKzs8Zh2hPQR6oeksJqpqeynGDrUAVRtuWtf-slbdA7wIZ6aRqgiDIi2gO-qSr6E3lDyCvT9JPFQaXAeS11IepL9fAQoAc5BS4dW3kRjsv9Zc520IzrJ7E6o0EkSR3SIsIsd4XEEMChCUMxB0BYWQ4V5v86yOZ45OJtOYlmQ9-OdFM0GZs0WLvD5s8WOos6ybyJZMG3eZWxZZMQ-5ObmQTjxgmz5khamKXjPF3BXRqIoGRAiYmDigvoTna0im-mWerfQ1sUe5pJwhGV5HZy9AsdTjOZqhQMOez4SP880UK06sW1bXxraBHQhVjRuRj3C8e1jc3M8cIj6kA-hOztobOn5SUWI7rzqWMg18NOjxhte15m5xqFlRa6dkRJd8zaJeAynNEoDi_UbnhRSLhq0ThNrSCZwfPhGkt-GgKcgRQPTaAverJMZYZFvT1Dxp1kkdiDETNiCkIULnOShjbTnVL3YrAExb3w9DsHyb1oT3SPjVYSTsJuzmmNswlMe6QCStJz1gOXZd8hH40E9MaS",
            "https://r.emails3.alertes.cadremploi.fr/tr/cl/4Dlqz4ZNkG11oDQwxqwRajkEJo-mOropfaU5TNojLgQYctcW_ZKjwus-hWd7XWUAeDpyj-MIw9sSfZMa5qsVEqhvB2tXmhwZgTMZ9qTPkJ4d3hYeGx1TcvOKbPNUUc1J5VKS4P_-jY6M-nGnh1555BhUp-wGLE7Kr3TPlbNHKUC_6YwBoOlEhskHTFhlVNFsLrX8BG2qWNd6jM0z_-48QKeM9m6FfPG-8I_gudpG55LpAZw9AoLsaEA10vg9v-_Zj5qmyvzwvMcAqkuWFN2Ok4rrZlUM0dZ3HwI78n-bk0QpV7dO4ByvpohGS8NTuR8ZvzNGTP6G8Xz1sStTVKs70UxU08Db78CMNuSLAEQ8slYRWK6KdXx7kt64HhBM77w8eFVLrHgA9xmtR3CbImi4vE0GHGaW0oskJmVANMkoY8AoX34KgFouEYaDpIgJw-uO43rgi8sokPKJKKkKn0mdebgAjyufi-xVoQSSMUxWvp0QYG0SvkyzqbVzXGNfHCjqRR9s9EaqUcGR2m-2noqKJl8YTl3MjEt6gNWu4U3D4AYFHwX7y8D9Jrgbf09IR0WFxrssfubO6BmVudbuQRxSUn1U3J3MVPAq-itNI-q61EGW7zeV_kcXlnWgZ58ca2iYxx-2gxQYtvRzyLe3kZQGL6gLIISY80-04Qiij2oKQopbOI3U4prcd0hqWsT4lWynPhUUsy1_zVV15cU60nYHplA2ji6KFUfYvDhMQdYiGI0MOqJFapu3_wpSaaLgRaGUf1opB0nH4o0xScIMxyyVJ2d-z6RpEQNPN559Zs90afi5ChqH9H_j4iHMYDA2EgIA7l_laglrhlIa9EgpRi2kxMzj2u29"
        };

        String[] jobTitles = {
            "Consultant JD Edwards H/F",
            "EXPERT(E) CITRIX F/H",
            "EXPERT(E) ACTIVE DIRECTORY F/H"
        };

        for (int i = 0; i < testUrls.length; i++) {
            System.out.println("─".repeat(70));
            System.out.println("Test " + (i + 1) + ": " + jobTitles[i]);
            System.out.println("─".repeat(70));
            System.out.println("Redirect URL: " + testUrls[i].substring(0, Math.min(100, testUrls[i].length())) + "...");
            System.out.println();

            try {
                String resolved = URLRedirectResolver.resolveCadreMploiURL(testUrls[i], jobTitles[i]);

                if (resolved != null) {
                    System.out.println("✅ SUCCESS!");
                    System.out.println("   Simplified URL: " + resolved);
                    System.out.println();

                    // Test if URL is accessible
                    System.out.print("   Testing accessibility... ");
                    boolean accessible = URLRedirectResolver.isURLAccessible(resolved);
                    if (accessible) {
                        System.out.println("✅ URL is accessible!");
                    } else {
                        System.out.println("⚠️  URL returned non-200 response");
                        System.out.println("      (May require session/cookies or job expired)");
                    }
                } else {
                    System.out.println("❌ FAILED to resolve redirect");
                }

            } catch (Exception e) {
                System.out.println("❌ ERROR: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println();
        }

        System.out.println("═".repeat(70));
        System.out.println("Test complete!");
        System.out.println("═".repeat(70));
    }
}
