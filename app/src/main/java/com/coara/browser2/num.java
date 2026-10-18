package com.coara.browser2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

public class num extends AppCompatActivity {

    private EditText editText;
    private TextView resultView;
    private Button buttonClear;
    private Button buttonEqual;
    private Button buttonAngle;
    private boolean degrees = true;
    private static final String STATE_DEGREES = "num_degrees";
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.num);

        editText = findViewById(R.id.editTextNumber);
        resultView = findViewById(R.id.textViewResult);
        buttonClear = findViewById(R.id.buttonClear);
        buttonEqual = findViewById(R.id.buttonEqual);
        buttonAngle = findViewById(R.id.buttonAngle);

        if (savedInstanceState != null) {
            degrees = savedInstanceState.getBoolean(STATE_DEGREES, true);
        }
        updateAngleButton();

        bindButton(R.id.button0, "0");
        bindButton(R.id.button1, "1");
        bindButton(R.id.button2, "2");
        bindButton(R.id.button3, "3");
        bindButton(R.id.button4, "4");
        bindButton(R.id.button5, "5");
        bindButton(R.id.button6, "6");
        bindButton(R.id.button7, "7");
        bindButton(R.id.button8, "8");
        bindButton(R.id.button9, "9");
        bindButton(R.id.buttonAdd, "+");
        bindButton(R.id.buttonSub, "-");
        bindButton(R.id.buttonMul, "*");
        bindButton(R.id.buttonDiv, "/");
        bindButton(R.id.buttonDot, ".");
        bindButton(R.id.buttonOpenParen, "(");
        bindButton(R.id.buttonCloseParen, ")");
        bindButton(R.id.buttonSin, "sin(");
        bindButton(R.id.buttonCos, "cos(");
        bindButton(R.id.buttonTan, "tan(");
        bindButton(R.id.buttonAsin, "asin(");
        bindButton(R.id.buttonAcos, "acos(");
        bindButton(R.id.buttonAtan, "atan(");
        bindButton(R.id.buttonSqrt, "sqrt(");
        bindButton(R.id.buttonCbrt, "cbrt(");
        bindButton(R.id.buttonLn, "ln(");
        bindButton(R.id.buttonLog, "log(");
        bindButton(R.id.buttonAbs, "abs(");
        bindButton(R.id.buttonExp, "exp(");
        bindButton(R.id.buttonSinh, "sinh(");
        bindButton(R.id.buttonCosh, "cosh(");
        bindButton(R.id.buttonTanh, "tanh(");
        bindButton(R.id.buttonFloor, "floor(");
        bindButton(R.id.buttonCeil, "ceil(");
        bindButton(R.id.buttonRound, "round(");
        bindButton(R.id.buttonMin, "min(");
        bindButton(R.id.buttonMax, "max(");
        bindButton(R.id.buttonPow, "pow(");
        bindButton(R.id.buttonComma, ",");
        bindButton(R.id.buttonPi, "π");
        bindButton(R.id.buttonE, "e");
        bindButton(R.id.buttonPower, "^");
        bindButton(R.id.buttonPercent, "%");
        bindButton(R.id.buttonFactorial, "!");
        bindButton(R.id.buttonExclamation, "!");

        buttonClear.setOnClickListener(v -> {
            editText.setText("");
            resultView.setText("");
            editText.requestFocus();
        });

        buttonEqual.setOnClickListener(v -> calculate());

        buttonAngle.setOnClickListener(v -> {
            degrees = !degrees;
            updateAngleButton();
            if (!editText.getText().toString().trim().isEmpty()) {
                calculate();
            }
        });

        findViewById(R.id.buttonBackspace).setOnClickListener(v -> deleteBeforeCursor());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_DEGREES, degrees);
        super.onSaveInstanceState(outState);
    }

    private void updateAngleButton() {
        buttonAngle.setText(degrees ? "DEG" : "RAD");
        buttonAngle.setContentDescription(degrees ? "角度モード" : "ラジアンモード");
    }

    private void bindButton(int id, String insertion) {
        Button button = findViewById(id);
        button.setOnClickListener(v -> insertText(insertion));
    }

    private void insertText(String insertion) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start < 0 || end < 0) {
            start = editText.length();
            end = start;
        }
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }
        start = Math.min(start, editText.length());
        end = Math.min(end, editText.length());
        editText.getText().replace(start, end, insertion);
        editText.setSelection(start + insertion.length());
        resultView.setText("");
    }

    private void deleteBeforeCursor() {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start < 0 || end < 0) {
            start = editText.length();
            end = start;
        }
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }
        if (start == end && start > 0) {
            start--;
        }
        if (start != end) {
            editText.getText().delete(start, end);
            editText.setSelection(start);
            resultView.setText("");
        }
    }

    private void calculate() {
        String input = editText.getText().toString().trim();
        if (input.isEmpty()) {
            resultView.setText("");
            return;
        }
        if (input.length() > 2048) {
            resultView.setText("数式が長すぎます");
            return;
        }
        if (handleHiddenCommand(input)) {
            return;
        }
        try {
            BigDecimal result = new ExpressionEvaluator().parse(input);
            String formatted = result.stripTrailingZeros().toPlainString();
            if (formatted.equals("-0")) {
                formatted = "0";
            }
            resultView.setText("= " + formatted);
        } catch (DivideByZeroException e) {
            resultView.setText("ゼロ除算エラー");
        } catch (Exception e) {
            resultView.setText("計算エラー: 入力式を確認してください");
        }
    }

    private boolean handleHiddenCommand(String input) {
        String command = input.replaceAll("\\s+", "").toLowerCase(Locale.US);
        switch (command) {
            case "sin(409)+!":
                showEasterEgg();
                return true;
            case "sin(410)+!":
                openDeveloperOptions();
                return true;
            case "sin(411)+!":
                openTargetWebsite();
                return true;
            default:
                return false;
        }
    }

    private void showEasterEgg() {
        resultView.setText("イースターエッグを発見！");
        new AlertDialog.Builder(this)
                .setTitle("🥚 イースターエッグ")
                .setMessage("隠しコマンドを発見しました！\n\n      (\\_/ )\n      ( •ᴗ• )\n      / >🥚\n\nCoara Browser の秘密のタマゴです。")
                .setPositiveButton("やった！", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void openDeveloperOptions() {
        resultView.setText("開発者向けオプションを開きます");
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "開発者向けオプションを開けませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTargetWebsite() {
        resultView.setText("ブラウザで Web サイトを開きます");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://abehiroshi.la.coocan.jp/"));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Web サイトを開けるブラウザが見つかりません", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Web サイトを開けませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    private static final class DivideByZeroException extends ArithmeticException {
        DivideByZeroException() {
            super("ゼロ除算");
        }
    }

    private class ExpressionEvaluator {
        private String str;
        private int pos;
        private int ch;

        BigDecimal parse(String expression) {
            str = expression;
            pos = -1;
            nextChar();
            BigDecimal value = parseExpression();
            skipWhitespace();
            if (ch != -1) {
                throw new IllegalArgumentException("不正な文字");
            }
            return value;
        }

        private void nextChar() {
            pos++;
            ch = pos < str.length() ? str.charAt(pos) : -1;
        }

        private void skipWhitespace() {
            while (ch != -1 && Character.isWhitespace((char) ch)) {
                nextChar();
            }
        }

        private boolean eat(int expected) {
            skipWhitespace();
            if (ch == expected) {
                nextChar();
                return true;
            }
            return false;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                if (eat('+')) {
                    value = value.add(parseTerm(), MATH_CONTEXT);
                } else if (eat('-')) {
                    value = value.subtract(parseTerm(), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseUnary();
            while (true) {
                if (eat('*')) {
                    value = value.multiply(parseUnary(), MATH_CONTEXT);
                } else if (eat('/')) {
                    BigDecimal divisor = parseUnary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new DivideByZeroException();
                    }
                    value = value.divide(divisor, MATH_CONTEXT);
                } else if (startsImplicitOperand()) {
                    value = value.multiply(parseUnary(), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private boolean startsImplicitOperand() {
            skipWhitespace();
            return (ch >= '0' && ch <= '9') || ch == '.' || ch == '(' || ch == 'π'
                    || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
        }

        private BigDecimal parseUnary() {
            if (eat('+')) {
                return parseUnary();
            }
            if (eat('-')) {
                return parseUnary().negate();
            }
            return parsePower();
        }

        private BigDecimal parsePower() {
            BigDecimal base = parsePostfix();
            if (eat('^')) {
                BigDecimal exponent = parseUnary();
                return power(base, exponent);
            }
            return base;
        }

        private BigDecimal parsePostfix() {
            BigDecimal value = parsePrimary();
            while (true) {
                if (eat('!')) {
                    value = factorial(value);
                } else if (eat('%')) {
                    value = value.divide(BigDecimal.valueOf(100), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal parsePrimary() {
            skipWhitespace();
            if (eat('(')) {
                BigDecimal value = parseExpression();
                if (!eat(')')) {
                    throw new IllegalArgumentException("閉じ括弧が必要");
                }
                return value;
            }

            if ((ch >= '0' && ch <= '9') || ch == '.') {
                int start = pos;
                int dots = 0;
                int digits = 0;
                while ((ch >= '0' && ch <= '9') || ch == '.') {
                    if (ch == '.') {
                        dots++;
                    } else {
                        digits++;
                    }
                    nextChar();
                }
                if (dots > 1 || digits == 0) {
                    throw new IllegalArgumentException("数値の形式が不正です");
                }
                return new BigDecimal(str.substring(start, pos));
            }

            if (ch == 'π' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                String name = parseIdentifier().toLowerCase(Locale.US);
                if (name.equals("pi") || name.equals("π")) {
                    return BigDecimal.valueOf(Math.PI);
                }
                if (name.equals("e")) {
                    return BigDecimal.valueOf(Math.E);
                }
                if (!isFunction(name)) {
                    throw new IllegalArgumentException("不明な関数または定数");
                }
                if (!eat('(')) {
                    throw new IllegalArgumentException("関数には括弧が必要です");
                }
                BigDecimal first = parseExpression();
                BigDecimal second = null;
                if (eat(',')) {
                    second = parseExpression();
                }
                if (!eat(')')) {
                    throw new IllegalArgumentException("閉じ括弧が必要");
                }
                return applyFunction(name, first, second);
            }

            throw new IllegalArgumentException("数値または関数が必要です");
        }

        private String parseIdentifier() {
            int start = pos;
            while (ch == 'π' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')) {
                nextChar();
            }
            return str.substring(start, pos);
        }

        private boolean isFunction(String name) {
            switch (name) {
                case "sin": case "cos": case "tan":
                case "asin": case "acos": case "atan":
                case "sinh": case "cosh": case "tanh":
                case "sqrt": case "cbrt": case "ln": case "log": case "log10":
                case "abs": case "exp": case "floor": case "ceil": case "round":
                case "min": case "max": case "pow":
                    return true;
                default:
                    return false;
            }
        }

        private BigDecimal applyFunction(String name, BigDecimal first, BigDecimal second) {
            if (name.equals("min") || name.equals("max") || name.equals("pow")) {
                if (second == null) {
                    throw new IllegalArgumentException("2つの引数が必要です");
                }
                if (name.equals("min")) {
                    return first.min(second);
                }
                if (name.equals("max")) {
                    return first.max(second);
                }
                return power(first, second);
            }
            if (second != null) {
                throw new IllegalArgumentException("引数が多すぎます");
            }

            double x = first.doubleValue();
            double value;
            switch (name) {
                case "sin":
                    value = Math.sin(toRadians(x));
                    break;
                case "cos":
                    value = Math.cos(toRadians(x));
                    break;
                case "tan":
                    double angle = toRadians(x);
                    if (Math.abs(Math.cos(angle)) < 1.0e-15) {
                        throw new ArithmeticException("定義されない値");
                    }
                    value = Math.tan(angle);
                    break;
                case "asin":
                    value = fromRadians(Math.asin(x));
                    break;
                case "acos":
                    value = fromRadians(Math.acos(x));
                    break;
                case "atan":
                    value = fromRadians(Math.atan(x));
                    break;
                case "sinh":
                    value = Math.sinh(x);
                    break;
                case "cosh":
                    value = Math.cosh(x);
                    break;
                case "tanh":
                    value = Math.tanh(x);
                    break;
                case "sqrt":
                    value = Math.sqrt(x);
                    break;
                case "cbrt":
                    value = Math.cbrt(x);
                    break;
                case "ln":
                    value = Math.log(x);
                    break;
                case "log":
                case "log10":
                    value = Math.log10(x);
                    break;
                case "abs":
                    return first.abs();
                case "exp":
                    value = Math.exp(x);
                    break;
                case "floor":
                    value = Math.floor(x);
                    break;
                case "ceil":
                    value = Math.ceil(x);
                    break;
                case "round":
                    value = Math.floor(x + 0.5d);
                    break;
                default:
                    throw new IllegalArgumentException("未対応の関数です");
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new ArithmeticException("計算できない値");
            }
            return BigDecimal.valueOf(value).round(new MathContext(15, RoundingMode.HALF_UP));
        }

        private double toRadians(double value) {
            return degrees ? Math.toRadians(value) : value;
        }

        private double fromRadians(double value) {
            return degrees ? Math.toDegrees(value) : value;
        }

        private BigDecimal power(BigDecimal base, BigDecimal exponent) {
            try {
                BigDecimal integral = exponent.stripTrailingZeros();
                if (integral.scale() <= 0) {
                    int power = integral.intValueExact();
                    if (Math.abs((long) power) > 10000) {
                        throw new ArithmeticException("指数が大きすぎます");
                    }
                    if (power >= 0) {
                        return base.pow(power, MATH_CONTEXT);
                    }
                    if (base.compareTo(BigDecimal.ZERO) == 0) {
                        throw new DivideByZeroException();
                    }
                    return BigDecimal.ONE.divide(base.pow(-power, MATH_CONTEXT), MATH_CONTEXT);
                }
            } catch (ArithmeticException e) {
                if (e instanceof DivideByZeroException) {
                    throw e;
                }
                if (e.getMessage() != null && e.getMessage().contains("大きすぎます")) {
                    throw e;
                }
            }
            double value = Math.pow(base.doubleValue(), exponent.doubleValue());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new ArithmeticException("計算できない値");
            }
            return BigDecimal.valueOf(value).round(new MathContext(15, RoundingMode.HALF_UP));
        }

        private BigDecimal factorial(BigDecimal value) {
            BigDecimal stripped = value.stripTrailingZeros();
            if (stripped.scale() > 0 || stripped.signum() < 0) {
                throw new IllegalArgumentException("階乗は0以上の整数で指定してください");
            }
            int n;
            try {
                n = stripped.intValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("階乗の値が大きすぎます");
            }
            if (n > 1000) {
                throw new IllegalArgumentException("階乗は1000以下で指定してください");
            }
            BigInteger result = BigInteger.ONE;
            for (int i = 2; i <= n; i++) {
                result = result.multiply(BigInteger.valueOf(i));
            }
            return new BigDecimal(result);
        }
    }
}
