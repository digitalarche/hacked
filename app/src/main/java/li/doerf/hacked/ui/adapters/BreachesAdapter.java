package li.doerf.hacked.ui.adapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.net.URL;
import java.util.List;

import li.doerf.hacked.CustomEvent;
import li.doerf.hacked.R;
import li.doerf.hacked.db.AppDatabase;
import li.doerf.hacked.db.daos.AccountDao;
import li.doerf.hacked.db.daos.BreachDao;
import li.doerf.hacked.db.entities.Account;
import li.doerf.hacked.db.entities.Breach;
import li.doerf.hacked.util.Analytics;
import li.doerf.hacked.util.RatingHelper;
import li.doerf.hacked.utils.AccountHelper;
import li.doerf.hacked.utils.BackgroundTaskHelper;

/**
 * Created by moo on 07/09/16.
 */
public class BreachesAdapter extends RecyclerView.Adapter<RecyclerViewHolder> {
    private final String LOGTAG = getClass().getSimpleName();
    private final Context myContext;
    private final BreachDao myBreachDao;
    private final AccountDao myAccountDao;
    private final Activity myActivity;
    private List<Breach> myBreachList;
    private ViewGroup myParentView;

    public BreachesAdapter(Activity activity, List<Breach> aList) {
        myActivity = activity;
        myContext = activity.getApplicationContext();
        myBreachList = aList;
        myBreachDao = AppDatabase.get(myContext).getBreachDao();
        myAccountDao = AppDatabase.get(myContext).getAccountDao();
    }

    @Override
    public RecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        myParentView = parent;
        CardView itemLayout = (CardView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_breach, parent, false);
        return new RecyclerViewHolder(itemLayout);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewHolder holder, int position) {
        Breach breach = myBreachList.get(position);
        final CardView cardView = (CardView) holder.getView();
        final long breachId = breach.getId();

        TextView title = cardView.findViewById(R.id.title);
        title.setText(breach.getTitle());

        TextView domain = cardView.findViewById(R.id.domain);
        domain.setText(breach.getDomain());

        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy/MM/dd");
        TextView breachDate = cardView.findViewById(R.id.breach_date);
        breachDate.setText(dtfOut.print(breach.getBreachDate()));

        TextView compromisedData = cardView.findViewById(R.id.compromised_data);
        compromisedData.setText(breach.getDataClasses());

        TextView description = cardView.findViewById(R.id.description);
        description.setText(Html.fromHtml(breach.getDescription()).toString());

        View statusIndicator = cardView.findViewById(R.id.status_indicator);
        Button acknowledge = cardView.findViewById(R.id.acknowledge);

        if ( ! breach.getAcknowledged() ) {
            statusIndicator.setBackgroundColor(getContext().getResources().getColor(R.color.account_status_breached));
            acknowledge.setVisibility(View.VISIBLE);
            acknowledge.setOnClickListener(v -> handleAcknowledgeClicked(breachId));
        } else {
            statusIndicator.setBackgroundColor(getContext().getResources().getColor(R.color.account_status_only_acknowledged));
            acknowledge.setVisibility(View.GONE);
        }

        View additionalFlagsLabel = cardView.findViewById(R.id.label_additional_flags);
        TextView additionalFlags = cardView.findViewById(R.id.additional_flags);

        if (breach.hasAdditionalFlags()) {
            additionalFlagsLabel.setVisibility(View.VISIBLE);
            additionalFlags.setVisibility(View.VISIBLE);

            additionalFlags.setText(getFlags(breach));

        } else {
            additionalFlagsLabel.setVisibility(View.GONE);
            additionalFlags.setVisibility(View.GONE);
        }

        ImageView logoView = cardView.findViewById(R.id.logo);
        if (breach.getLogoPath() != null && breach.getLogoPath() != "") {
            new BackgroundTaskHelper<Bitmap>().runInBackgroundAndConsumeOnMain(
                    () -> {
                        URL url = new URL(breach.getLogoPath());
                        return BitmapFactory.decodeStream(url.openConnection().getInputStream());
                    },
                    (result) -> {
                        logoView.setVisibility(View.VISIBLE);
                        logoView.setImageBitmap(result);
                    }
            );
        } else {
            logoView.setVisibility(View.GONE);
        }
    }

    private String getFlags(Breach breach) {
        StringBuilder flags = new StringBuilder();

        if (! breach.getVerified()) {
            flags.append(getContext().getString(R.string.unverified)).append(" ");
        }
        if (breach.getFabricated()) {
            flags.append(getContext().getString(R.string.fabricated)).append(" ");
        }
        if (breach.getRetired()) {
            flags.append(getContext().getString(R.string.retired)).append(" ");
        }
        if (breach.getSensitive()) {
            flags.append(getContext().getString(R.string.sensitive)).append(" ");
        }
        if (breach.getSpamList()) {
            flags.append(getContext().getString(R.string.spam_list)).append(" ");
        }

        return flags.toString();
    }

    private void handleAcknowledgeClicked(long breachId) {
        new BackgroundTaskHelper<Boolean>().runInBackgroundAndConsumeOnMain(
                () -> {
                    Breach breach = myBreachDao.findById(breachId);
                    if ( breach == null ) {
                        Log.w(LOGTAG, "no breack found with id " + breachId);
                        return false;
                    }

                    setBreachAcknowledged(breach);
                    updateAccountIsHacked(breach.getAccount());
                    return true;
                },
                (result) -> {
                    notifyDataSetChanged();
                    if (!result) return;
                    Snackbar.make(myParentView, getContext().getString(R.string.breach_acknowledged), Snackbar.LENGTH_SHORT).show();
                    new RatingHelper(myActivity).setRatingCounterBelowthreshold();
                }
        );
    }

    private void setBreachAcknowledged(Breach breach) {
        breach.setAcknowledged(true);
        myBreachDao.update(breach);
        Log.d(LOGTAG, "breach updated - acknowledge = true");
        Analytics.Companion.trackCustomEvent(CustomEvent.BREACH_ACKNOWLEDGED);
    }

    @Override
    public int getItemCount() {
        return myBreachList.size();
    }

    private void updateAccountIsHacked(Long accountId) {
        List<Account> accounts = myAccountDao.findById(accountId);

        for (Account account : accounts) {
            if (!account.getHacked()) {
                return;
            }

            new AccountHelper(myContext).updateBreachCounts(account);

            if (myBreachDao.countUnacknowledged(accountId) == 0) {
                Log.d(LOGTAG, "account has only acknowledged breaches");
                account.setHacked(false);
            }

            myAccountDao.update(account);
            Log.d(LOGTAG, "account updated - hacked = " + account.getHacked() + " numBreaches = " + account.getNumBreaches() + " numAcknowledgedBreaches = " + account.getNumAcknowledgedBreaches());
        }
    }

    public void addItems(List<Breach> list) {
        myBreachList = list;
        notifyDataSetChanged();
    }

    private Context getContext() {
        return myContext;
    }

}
