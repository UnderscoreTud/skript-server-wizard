package me.tud;

public class StringDistance {

    // https://en.wikipedia.org/wiki/Levenshtein_distance#Iterative_with_full_matrix
    public static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++)
            dp[i][0] = i;

        for (int j = 1; j <= n; j++)
            dp[0][j] = j;

        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[m][n];
    }

}
