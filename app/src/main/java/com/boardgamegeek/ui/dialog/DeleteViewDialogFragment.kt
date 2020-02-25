package com.boardgamegeek.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProviders
import com.boardgamegeek.R
import com.boardgamegeek.extensions.load
import com.boardgamegeek.provider.BggContract.CollectionViews
import com.boardgamegeek.ui.viewmodel.CollectionViewViewModel
import com.boardgamegeek.util.fabric.CollectionViewManipulationEvent
import org.jetbrains.anko.support.v4.act

class DeleteViewDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentResolver = requireContext().contentResolver
        val cursor = contentResolver.load(CollectionViews.CONTENT_URI, arrayOf(CollectionViews._ID, CollectionViews.NAME))
        val viewModel = ViewModelProviders.of(act).get(CollectionViewViewModel::class.java)
        val toast = Toast.makeText(requireContext(), R.string.msg_collection_view_deleted, Toast.LENGTH_SHORT) // TODO improve message

        return Builder(requireContext(), R.style.Theme_bgglight_Dialog_Alert)
                .setTitle(R.string.title_delete_view)
                .setCursor(cursor, { _, which ->
                    Builder(requireContext())
                            .setTitle(R.string.are_you_sure_title)
                            .setMessage(R.string.are_you_sure_delete_collection_view)
                            .setCancelable(true)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                if (cursor?.moveToPosition(which) == true) {
                                    toast.show()
                                    CollectionViewManipulationEvent.log("Delete")
                                    val viewId = cursor.getLong(0)
                                    viewModel.deleteView(viewId)
                                }
                            }
                            .setNegativeButton(R.string.no, null)
                            .create()
                            .show()
                }, CollectionViews.NAME)
                .create()
    }

    companion object {
        @JvmStatic
        fun newInstance(): DeleteViewDialogFragment {
            return DeleteViewDialogFragment()
        }
    }
}
