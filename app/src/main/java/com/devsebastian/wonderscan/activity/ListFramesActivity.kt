/*
 * Copyright (C) 2021 Dev Sebastian
 * This file is part of WonderScan <https://github.com/devsebastian/WonderScan>.
 *
 * WonderScan is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * WonderScan is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with WonderScan.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.devsebastian.wonderscan.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.devsebastian.wonderscan.*
import com.devsebastian.wonderscan.adapter.ProgressFramesAdapter
import com.devsebastian.wonderscan.data.Document
import com.devsebastian.wonderscan.viewmodel.ListFrameActivityViewModel
import com.devsebastian.wonderscan.viewmodel.ListFrameActivityViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ListFramesActivity : BaseActivity() {
    private lateinit var framesAdapter: ProgressFramesAdapter

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_list_frames, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    viewModel.exportPdf(uri)
                }
            }
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_export_pdf -> {
                viewModel.sendCreateFileIntent(
                    "application/pdf",
                    resultLauncher
                )
            }
            R.id.menu_delete -> {
                showConfirmDeleteDialog()
            }
            R.id.menu_rename -> {
                showRenameDialog()
            }
            android.R.id.home -> {
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun showRenameDialog() {
        lifecycleScope.launch(Dispatchers.Main) {
            val document: Document = viewModel.getDocument()
            val builder = AlertDialog.Builder(this@ListFramesActivity)
            builder.setTitle("Rename")
            val frameLayout = FrameLayout(application)
            val editText = EditText(application)
            val layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(50, 12, 50, 12)
            editText.layoutParams = layoutParams
            editText.setText(document.name)
            frameLayout.addView(editText)
            builder.setView(frameLayout)
            builder.setNegativeButton("Cancel", null)
            builder.setPositiveButton("Save") { _: DialogInterface?, _: Int ->
                document.name = editText.text.toString()
                viewModel.updateDocument(document)
            }
            builder.create().show()
        }
    }

    private fun showConfirmDeleteDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm Delete")
        builder.setMessage("Are you sure you want to delete this document. You won't be able to recover the document later!")
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton("Delete") { _, _ ->
            viewModel.delete()
            finish()
        }
        builder.create().show()
    }

    lateinit var viewModel: ListFrameActivityViewModel
    var docId: String? = null

    override fun onResume() {
        super.onResume()
        viewModel.processUnprocessedFrames(docId!!)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = ""
        setContentView(R.layout.activity_list_frames)
        setSupportActionBar(findViewById(R.id.toolbar))
        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
        }
        docId = intent.getStringExtra(getString(R.string.intent_document_id))
        if (docId == null) {
            Toast.makeText(this, getString(R.string.toast_error_message), Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }

        framesAdapter = ProgressFramesAdapter(this, docId!!, ArrayList())

        (application as MyApplication).database?.let { db ->
            viewModel = ViewModelProvider(
                this,
                ListFrameActivityViewModelFactory(
                    application as MyApplication,
                    db.documentDao(),
                    db.frameDao(),
                    docId!!
                )
            ).get(ListFrameActivityViewModel::class.java)
        }

        viewModel.document.observe(this) { doc ->
            doc?.name?.let {
                (findViewById<View?>(R.id.toolbar_title) as TextView).text = it
            }
        }

        viewModel.frames.observe(this) { frames ->
            var datasetChanged = true
            if (framesAdapter.frames.size == frames.size) datasetChanged = false
            framesAdapter.frames = frames
            if (datasetChanged)
                framesAdapter.notifyDataSetChanged()
            else
                for (i in frames.indices) {
                    framesAdapter.notifyItemChanged(i)
                }
        }

        val itemTouchHelper = ItemTouchHelper(getItemTouchHelperCallback())
        val fab = findViewById<View?>(R.id.fab)
        val recyclerView = findViewById<RecyclerView?>(R.id.rv_frames)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = framesAdapter
        fab.setOnClickListener {
            val i = Intent(this@ListFramesActivity, ScanActivity::class.java)
            i.putExtra(getString(R.string.intent_document_id), docId)
            startActivity(i)
            finish()
        }
        itemTouchHelper.attachToRecyclerView(recyclerView)

        recyclerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    if (framesAdapter.isSwapped) {
                        framesAdapter.isSwapped = false
                        viewModel.update(framesAdapter.frames)
                    }
                }
            }
            false
        }

        Toast.makeText(
            this,
            "Hint: Re-order pages by long pressing a page and dragging it to the appropriate position",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getItemTouchHelperCallback(): ItemTouchHelper.Callback {
        return object : ItemTouchHelper.Callback() {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                framesAdapter.swap(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeFlag(
                    ItemTouchHelper.ACTION_STATE_DRAG,
                    ItemTouchHelper.DOWN or ItemTouchHelper.UP or ItemTouchHelper.START or ItemTouchHelper.END
                )
            }
        }
    }

    companion object {
        const val VIEW_PAGE_ACTIVITY = 101
    }
}