package com.codingblocks.cbonlineapp.admin

import android.graphics.Rect
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.admin.doubts.DoubtsFragment
import com.codingblocks.cbonlineapp.admin.overview.AdminOverviewFragment
import com.codingblocks.cbonlineapp.mycourse.MyCourseViewModel
import com.codingblocks.cbonlineapp.util.Components
import com.codingblocks.cbonlineapp.util.extensions.getPrefs
import com.codingblocks.cbonlineapp.util.extensions.replaceFragmentSafely
import kotlinx.android.synthetic.main.activity_admin.*
import org.jetbrains.anko.contentView
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminActivity : AppCompatActivity(), FragmentChangeListener {

    override fun openInbox(conversationId: String) {
        replaceFragmentSafely(
            fragment = InboxFragment.newInstance(conversationId),
            containerViewId = R.id.pagerAdmin,
            allowStateLoss = true
        )
    }

    private val viewModel by viewModel<MyCourseViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        setSupportActionBar(toolbarAdmin)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val roleId = getPrefs().SP_ROLE_ID
        if (roleId == 1 || roleId == 3) {
            initializeUI()
        } else {
            Components.showConfirmation(this, "admin") {
                finish()
            }
        }
    }

    private fun initializeUI() {
        bottomNavAdmin.setOnNavigationItemSelectedListener {
            return@setOnNavigationItemSelectedListener when (it.itemId) {
                R.id.menu_overview -> {
                    replaceFragmentSafely(
                        fragment = AdminOverviewFragment(),
                        containerViewId = R.id.pagerAdmin,
                        allowStateLoss = true
                    )
                    true
                }
                R.id.menu_doubts -> {
                    replaceFragmentSafely(
                        fragment = DoubtsFragment(),
                        containerViewId = R.id.pagerAdmin,
                        allowStateLoss = true
                    )
                    true
                }
                R.id.menu_inbox -> {
                    replaceFragmentSafely(
                        fragment = InboxFragment(),
                        containerViewId = R.id.pagerAdmin,
                        allowStateLoss = true
                    )
                    true
                }
                else -> false
            }
        }
        replaceFragmentSafely(
            fragment = AdminOverviewFragment(),
            containerViewId = R.id.pagerAdmin,
            allowStateLoss = true
        )
    }

    private fun onKeyboardShown(state: Boolean) {
        bottomNavAdmin.isVisible = state
    }

    private var mKeyboardVisible: Boolean = false

    private val mLayoutKeyboardVisibilityListener = {
        val rectangle = Rect()
        val contentView = contentView!!
        contentView.getWindowVisibleDisplayFrame(rectangle)
        val screenHeight = contentView.rootView.height

        // r.bottom is the position above soft keypad or device button.
        // If keypad is shown, the rectangle.bottom is smaller than that before.
        val keypadHeight = screenHeight.minus(rectangle.bottom)
        // 0.15 ratio is perhaps enough to determine keypad height.
        val isKeyboardNowVisible = keypadHeight > screenHeight * 0.15

        if (mKeyboardVisible !== isKeyboardNowVisible) {
            if (isKeyboardNowVisible) {
                onKeyboardShown(false)
            } else {
                onKeyboardShown(true)
            }
        }

        mKeyboardVisible = isKeyboardNowVisible
    }

    override fun onResume() {
        super.onResume()
        contentView!!.viewTreeObserver
            .addOnGlobalLayoutListener(mLayoutKeyboardVisibilityListener)
    }

    override fun onPause() {
        super.onPause()
        contentView!!.viewTreeObserver
            .removeOnGlobalLayoutListener(mLayoutKeyboardVisibilityListener)
    }
}