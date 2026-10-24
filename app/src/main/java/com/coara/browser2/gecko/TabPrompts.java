package com.coara.browser2.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.net.Uri;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import java.net.URISyntaxException;

final class TabPrompts implements GeckoSession.PromptDelegate {
    private final BrowserView view;

    TabPrompts(BrowserView view) {
        this.view = view;
    }

    private Activity activity() {
        Context c = view.getContext();
        int depth = 0;
        while (c != null && depth++ < 8) {
            if (c instanceof Activity) {
                Activity a = (Activity) c;
                return a.isFinishing() || a.isDestroyed() ? null : a;
            }
            if (c instanceof ContextWrapper) {
                c = ((ContextWrapper) c).getBaseContext();
            } else {
                break;
            }
        }
        return null;
    }

    private BrowserHost host() {
        Activity a = activity();
        return a instanceof BrowserHost ? (BrowserHost) a : null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final class Slot<T> {
        final GeckoResult<T> result = new GeckoResult<>();
        boolean done;

        void finish(T value) {
            if (done) {
                return;
            }
            done = true;
            result.complete(value);
        }
    }

    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session, final AlertPrompt prompt) {
        Activity a = activity();
        if (a == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final Slot<PromptResponse> slot = new Slot<>();
        new AlertDialog.Builder(a)
                .setTitle(nz(prompt.title))
                .setMessage(nz(prompt.message))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.dismiss());
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        slot.finish(prompt.dismiss());
                    }
                })
                .show();
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession session, final ButtonPrompt prompt) {
        Activity a = activity();
        if (a == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final Slot<PromptResponse> slot = new Slot<>();
        new AlertDialog.Builder(a)
                .setTitle(nz(prompt.title))
                .setMessage(nz(prompt.message))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.confirm(ButtonPrompt.Type.POSITIVE));
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.confirm(ButtonPrompt.Type.NEGATIVE));
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        slot.finish(prompt.dismiss());
                    }
                })
                .show();
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onTextPrompt(GeckoSession session, final TextPrompt prompt) {
        Activity a = activity();
        if (a == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final Slot<PromptResponse> slot = new Slot<>();
        final EditText input = new EditText(a);
        input.setText(nz(prompt.defaultValue));
        input.setSingleLine(true);
        LinearLayout box = new LinearLayout(a);
        int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(a)
                .setTitle(nz(prompt.title))
                .setMessage(nz(prompt.message))
                .setView(box)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.confirm(input.getText().toString()));
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.dismiss());
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        slot.finish(prompt.dismiss());
                    }
                })
                .show();
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession session, final ChoicePrompt prompt) {
        Activity a = activity();
        if (a == null || prompt.choices == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final List<ChoicePrompt.Choice> flat = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        flatten(prompt.choices, flat, labels, "");
        final Slot<PromptResponse> slot = new Slot<>();
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        if (prompt.title != null && !prompt.title.isEmpty()) {
            b.setTitle(prompt.title);
        } else if (prompt.message != null && !prompt.message.isEmpty()) {
            b.setTitle(prompt.message);
        }
        final CharSequence[] items = labels.toArray(new CharSequence[0]);
        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            final boolean[] checked = new boolean[flat.size()];
            for (int i = 0; i < flat.size(); i++) {
                checked[i] = flat.get(i).selected;
            }
            b.setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface d, int which, boolean isChecked) {
                    checked[which] = isChecked;
                }
            });
            b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i] && !flat.get(i).disabled) {
                            ids.add(flat.get(i).id);
                        }
                    }
                    slot.finish(prompt.confirm(ids.toArray(new String[0])));
                }
            });
            b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    slot.finish(prompt.dismiss());
                }
            });
        } else {
            int selected = -1;
            for (int i = 0; i < flat.size(); i++) {
                if (flat.get(i).selected) {
                    selected = i;
                    break;
                }
            }
            b.setSingleChoiceItems(items, selected, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    ChoicePrompt.Choice c = flat.get(which);
                    if (c.disabled || c.separator) {
                        return;
                    }
                    slot.finish(prompt.confirm(c.id));
                    d.dismiss();
                }
            });
        }
        b.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface d) {
                slot.finish(prompt.dismiss());
            }
        });
        b.show();
        return slot.result;
    }

    private void flatten(ChoicePrompt.Choice[] choices, List<ChoicePrompt.Choice> flat, List<String> labels, String indent) {
        for (ChoicePrompt.Choice c : choices) {
            if (c == null) {
                continue;
            }
            if (c.items != null && c.items.length > 0) {
                flat.add(c);
                labels.add(indent + nz(c.label));
                flatten(c.items, flat, labels, indent + "  ");
            } else {
                flat.add(c);
                labels.add(indent + nz(c.label));
            }
        }
    }

    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(GeckoSession session, final AuthPrompt prompt) {
        BrowserHost host = host();
        if (host == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final Slot<PromptResponse> slot = new Slot<>();
        String target = prompt.authOptions == null ? "" : nz(prompt.authOptions.uri);
        boolean failed = prompt.authOptions != null
                && (prompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.PREVIOUS_FAILED) != 0;
        boolean onlyPassword = prompt.authOptions != null
                && (prompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0;
        String realm = nz(prompt.title);
        String user = prompt.authOptions == null ? "" : nz(prompt.authOptions.username);
        String pass = prompt.authOptions == null ? "" : nz(prompt.authOptions.password);
        String hostName = target;
        try {
            Uri u = Uri.parse(target);
            if (u.getHost() != null) {
                hostName = u.getHost();
            }
        } catch (Exception ignored) {
        }
        host.requestAuth(view, hostName, realm, failed, user, pass, new BrowserHost.AuthCallback() {
            @Override
            public void proceed(String u, String p) {
                if (onlyPassword) {
                    slot.finish(prompt.confirm(p));
                } else {
                    slot.finish(prompt.confirm(u, p));
                }
            }

            @Override
            public void cancel() {
                slot.finish(prompt.dismiss());
            }
        });
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session, final FilePrompt prompt) {
        final BrowserHost host = host();
        if (host == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final Slot<PromptResponse> slot = new Slot<>();
        boolean multiple = prompt.type == FilePrompt.Type.MULTIPLE;
        boolean capture = prompt.capture != FilePrompt.Capture.NONE;
        host.chooseFiles(prompt.mimeTypes, multiple, capture, new ValueCallback<Uri[]>() {
            @Override
            public void onReceiveValue(Uri[] uris) {
                if (uris == null || uris.length == 0) {
                    slot.finish(prompt.dismiss());
                } else if (uris.length == 1) {
                    slot.finish(prompt.confirm(view.getContext(), uris[0]));
                } else {
                    slot.finish(prompt.confirm(view.getContext(), uris));
                }
            }
        });
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onDateTimePrompt(GeckoSession session, final DateTimePrompt prompt) {
        Activity a = activity();
        if (a == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final Slot<PromptResponse> slot = new Slot<>();
        final Calendar cal = Calendar.getInstance();
        parseInto(cal, prompt.defaultValue, prompt.type);
        final DialogInterface.OnCancelListener cancel = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface d) {
                slot.finish(prompt.dismiss());
            }
        };
        if (prompt.type == DateTimePrompt.Type.TIME) {
            TimePickerDialog dlg = new TimePickerDialog(a, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(android.widget.TimePicker p, int h, int m) {
                    slot.finish(prompt.confirm(String.format(Locale.US, "%02d:%02d", h, m)));
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
            dlg.setOnCancelListener(cancel);
            dlg.show();
            return slot.result;
        }
        final int type = prompt.type;
        DatePickerDialog dlg = new DatePickerDialog(a, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(android.widget.DatePicker p, int y, int mo, int d) {
                if (type == DateTimePrompt.Type.MONTH) {
                    slot.finish(prompt.confirm(String.format(Locale.US, "%04d-%02d", y, mo + 1)));
                } else if (type == DateTimePrompt.Type.DATETIME_LOCAL) {
                    final int fy = y;
                    final int fm = mo;
                    final int fd = d;
                    Activity again = activity();
                    if (again == null) {
                        slot.finish(prompt.dismiss());
                        return;
                    }
                    TimePickerDialog t = new TimePickerDialog(again, new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(android.widget.TimePicker tp, int h, int m) {
                            slot.finish(prompt.confirm(String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d", fy, fm + 1, fd, h, m)));
                        }
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
                    t.setOnCancelListener(cancel);
                    t.show();
                } else {
                    slot.finish(prompt.confirm(String.format(Locale.US, "%04d-%02d-%02d", y, mo + 1, d)));
                }
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dlg.setOnCancelListener(cancel);
        dlg.show();
        return slot.result;
    }

    private static void parseInto(Calendar cal, String value, int type) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            String[] dt = value.split("T");
            String date = dt[0];
            String time = dt.length > 1 ? dt[1] : null;
            if (type == DateTimePrompt.Type.TIME) {
                time = value;
                date = null;
            }
            if (date != null && date.length() >= 7) {
                String[] p = date.split("-");
                cal.set(Calendar.YEAR, Integer.parseInt(p[0]));
                cal.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
                if (p.length > 2 && !p[2].isEmpty() && !p[2].startsWith("W")) {
                    cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
                }
            }
            if (time != null) {
                String[] t = time.split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(t[1]));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public GeckoResult<PromptResponse> onColorPrompt(GeckoSession session, final ColorPrompt prompt) {
        Activity a = activity();
        if (a == null) {
            return GeckoResult.fromValue(prompt.dismiss());
        }
        final Slot<PromptResponse> slot = new Slot<>();
        final EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(nz(prompt.defaultValue).isEmpty() ? "#000000" : prompt.defaultValue);
        new AlertDialog.Builder(a)
                .setTitle("#RRGGBB")
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String v = input.getText().toString().trim();
                        if (!v.matches("#[0-9a-fA-F]{6}")) {
                            slot.finish(prompt.dismiss());
                        } else {
                            slot.finish(prompt.confirm(v));
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.dismiss());
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        slot.finish(prompt.dismiss());
                    }
                })
                .show();
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(GeckoSession session, PopupPrompt prompt) {
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
    }

    @Override
    public GeckoResult<PromptResponse> onRedirectPrompt(GeckoSession session, RedirectPrompt prompt) {
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
    }

    @Override
    public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession session, BeforeUnloadPrompt prompt) {
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
    }

    @Override
    public GeckoResult<PromptResponse> onRepostConfirmPrompt(GeckoSession session, final RepostConfirmPrompt prompt) {
        Activity a = activity();
        if (a == null) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY));
        }
        final Slot<PromptResponse> slot = new Slot<>();
        new AlertDialog.Builder(a)
                .setMessage("フォームを再送信しますか?")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.confirm(AllowOrDeny.ALLOW));
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        slot.finish(prompt.confirm(AllowOrDeny.DENY));
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        slot.finish(prompt.confirm(AllowOrDeny.DENY));
                    }
                })
                .show();
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onWebAuthnRelatedOriginPrompt(
            GeckoSession session, final WebAuthnRelatedOriginPrompt prompt) {
        Activity a = activity();
        String origin = nz(prompt.origin);
        String rpId = nz(prompt.rpId);
        URI parsedOrigin;
        try {
            parsedOrigin = new URI(origin);
        } catch (URISyntaxException | IllegalArgumentException error) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY));
        }
        String scheme = parsedOrigin.getScheme();
        String path = parsedOrigin.getRawPath();
        String originHost = parsedOrigin.getHost();
        int originPort = parsedOrigin.getPort();
        String expectedAuthority = originHost == null ? "" : originHost + (originPort == 443 ? ":443" : "");
        boolean secureOrigin = origin.length() <= 2048
                && scheme != null && "https".equalsIgnoreCase(scheme)
                && originHost != null && !originHost.isEmpty() && !originHost.endsWith(".")
                && parsedOrigin.getRawAuthority() != null
                && parsedOrigin.getRawAuthority().equalsIgnoreCase(expectedAuthority)
                && parsedOrigin.getRawUserInfo() == null
                && parsedOrigin.getRawQuery() == null && parsedOrigin.getRawFragment() == null
                && (path == null || path.isEmpty() || "/".equals(path))
                && (originPort == -1 || originPort == 443)
                && originHost.matches("(?i)(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(?:\\.(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?))*");
        boolean validRpId = rpId.length() <= 253
                && rpId.matches("(?i)(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(?:\\.(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?))*");
        if (a == null || !secureOrigin || !validRpId) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY));
        }
        String action = prompt.isCreate ? "作成" : "使用";
        String message = "このサイトが、関連オリジンのパスキーを" + action + "しようとしています。\n\n"
                + "要求元: " + origin + "\n"
                + "RP ID: " + rpId + "\n\n"
                + "この要求元と RP ID の両方を信頼できる場合のみ許可してください。";
        final Slot<PromptResponse> slot = new Slot<>();
        try {
            new AlertDialog.Builder(a)
                    .setTitle("パスキーの関連サイト確認")
                    .setMessage(message)
                    .setPositiveButton("許可", (dialog, which) -> {
                        if (!slot.done) {
                            slot.finish(prompt.confirm(AllowOrDeny.ALLOW));
                        }
                    })
                    .setNegativeButton("拒否", (dialog, which) -> {
                        if (!slot.done) {
                            slot.finish(prompt.confirm(AllowOrDeny.DENY));
                        }
                    })
                    .setOnCancelListener(dialog -> {
                        if (!slot.done) {
                            slot.finish(prompt.confirm(AllowOrDeny.DENY));
                        }
                    })
                    .show();
        } catch (RuntimeException error) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY));
        }
        return slot.result;
    }

    @Override
    public GeckoResult<PromptResponse> onSharePrompt(GeckoSession session, SharePrompt prompt) {
        return GeckoResult.fromValue(prompt.confirm(SharePrompt.Result.ABORT));
    }
}
