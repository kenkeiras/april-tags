package april.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil
{
    /**
     *  Method to replace occurrences of named environment variables
     *  in a string with their true values. Note that this method only
     *  accepts env. variables which are followed by non word
     *  character:

     * e.g. "$ENV_variable/" would replace the everything but the
     * slash with the value of the environment variable "ENV_variable"
     *
     */
    public static String replaceEnvironmentVariables(String value)
    {
        Pattern pattern = Pattern.compile("\\$\\w*\\W");
        Matcher matcher = pattern.matcher(value);

        String resolved = new String(value);

        while (matcher.find()) {
            String match = matcher.group();
            String variableName = matcher.group().split("\\W")[1];
            String variableValue = System.getenv(variableName);

            if(variableValue != null){
                // Keep last character of matched regex
                variableValue += match.charAt(match.length() -1);
                resolved = resolved.replace(match, variableValue);
            }else{
                System.err.println("Ignoring unknown environment variable: "+match);
            }

        }

        return resolved;
    }
}
