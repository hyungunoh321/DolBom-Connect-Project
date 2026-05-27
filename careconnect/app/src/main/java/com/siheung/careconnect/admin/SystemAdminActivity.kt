package com.siheung.careconnect.admin

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivitySystemAdminBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SystemAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemAdminBinding
    private lateinit var userAdapter: AdminUserAdapter
    private var allUsers: List<AdminUserRow> = emptyList()
    private var selectedRoleFilter = ROLE_FILTER_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySystemAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        setupFilters()
        setupRecyclerView()
        loadUsers()
    }

    private fun setupFilters() {
        val filters = listOf(ROLE_FILTER_ALL) + USER_ROLES
        binding.spRoleFilter.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            filters
        )
        binding.spRoleFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRoleFilter = filters[position]
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun setupRecyclerView() {
        userAdapter = AdminUserAdapter(
            onApplyRole = { user, newRole -> updateUserRole(user, newRole) },
            onShowDetail = { user -> showUserDetail(user) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = userAdapter
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            setLoading(true)
            try {
                val result = SupabaseClientProvider.client
                    .postgrest["users"]
                    .select(Columns.raw("id,username,role,created_at"))

                allUsers = result.decodeList<AdminUserRow>()
                applyFilters()
            } catch (e: Exception) {
                allUsers = emptyList()
                applyFilters()
                Toast.makeText(
                    this@SystemAdminActivity,
                    "회원 정보를 불러오지 못했습니다. RLS 정책을 확인해주세요.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun applyFilters() {
        val keyword = binding.etSearch.text.toString().trim()
        val filtered = allUsers.filter { user ->
            val matchesRole = selectedRoleFilter == ROLE_FILTER_ALL || user.role == selectedRoleFilter
            val matchesKeyword = keyword.isBlank() ||
                user.username.contains(keyword, ignoreCase = true) ||
                user.role.contains(keyword, ignoreCase = true)
            matchesRole && matchesKeyword
        }

        userAdapter.submitList(filtered)
        binding.tvResultCount.text = "회원 ${filtered.size}명"
        binding.tvEmpty.visibility = if (!binding.progressBar.isShown && filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.rvUsers.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
    }

    private fun updateUserRole(user: AdminUserRow, newRole: String) {
        if (user.role == newRole) {
            Toast.makeText(this, "이미 적용된 권한입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                SupabaseClientProvider.client
                    .postgrest["users"]
                    .update(
                        {
                            set("role", newRole)
                        }
                    ) {
                        filter {
                            eq("id", user.id)
                        }
                    }

                allUsers = allUsers.map {
                    if (it.id == user.id) it.copy(role = newRole) else it
                }
                applyFilters()
                Toast.makeText(this@SystemAdminActivity, "권한이 변경되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SystemAdminActivity,
                    "권한 변경에 실패했습니다. 시스템 관리자 RLS 정책을 확인해주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showUserDetail(user: AdminUserRow) {
        lifecycleScope.launch {
            val children = try {
                SupabaseClientProvider.client
                    .postgrest["children"]
                    .select(Columns.raw("parent_id,name,birth_date,income_level")) {
                        filter {
                            eq("parent_id", user.id)
                        }
                    }
                    .decodeList<ChildRow>()
            } catch (e: Exception) {
                emptyList()
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_admin_user_detail, null)
            val dialog = AlertDialog.Builder(this@SystemAdminActivity)
                .setView(dialogView)
                .create()

            dialogView.findViewById<TextView>(R.id.tvDetailTitle).text = user.username
            dialogView.findViewById<TextView>(R.id.tvDetailBody).text = buildDetailText(user, children)
            dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun buildDetailText(user: AdminUserRow, children: List<ChildRow>): String {
        val childText = if (children.isEmpty()) {
            "등록된 자녀 정보 없음"
        } else {
            children.joinToString("\n") {
                val income = it.incomeLevel?.toString() ?: "-"
                "${it.name} / ${it.birthDate} / 소득분위 $income"
            }
        }

        return """
            회원 ID: ${user.id}
            현재 권한: ${user.role}
            가입일: ${user.createdAt ?: "-"}

            자녀 정보
            $childText
        """.trimIndent()
    }

    companion object {
        private const val ROLE_FILTER_ALL = "전체 권한"
        val USER_ROLES = listOf("보호자", "관리자", "시스템관리자")
    }
}

@Serializable
data class AdminUserRow(
    val id: String = "",
    val username: String = "",
    val role: String = "",
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class ChildRow(
    @SerialName("parent_id")
    val parentId: String = "",
    val name: String = "",
    @SerialName("birth_date")
    val birthDate: String = "",
    @SerialName("income_level")
    val incomeLevel: Int? = null
)

class AdminUserAdapter(
    private val onApplyRole: (AdminUserRow, String) -> Unit,
    private val onShowDetail: (AdminUserRow) -> Unit
) : RecyclerView.Adapter<AdminUserAdapter.ViewHolder>() {

    private var items: List<AdminUserRow> = emptyList()

    fun submitList(newItems: List<AdminUserRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvUserId: TextView = itemView.findViewById(R.id.tvUserId)
        private val tvCurrentRole: TextView = itemView.findViewById(R.id.tvCurrentRole)
        private val spRole: Spinner = itemView.findViewById(R.id.spRole)
        private val btnApplyRole: Button = itemView.findViewById(R.id.btnApplyRole)
        private val btnDetail: Button = itemView.findViewById(R.id.btnDetail)

        fun bind(user: AdminUserRow) {
            tvUsername.text = user.username.ifBlank { "이름 없는 회원" }
            tvUserId.text = user.id
            tvCurrentRole.text = user.role.ifBlank { "권한 없음" }

            val roleAdapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_dropdown_item,
                SystemAdminActivity.USER_ROLES
            )
            spRole.adapter = roleAdapter
            spRole.setSelection(SystemAdminActivity.USER_ROLES.indexOf(user.role).coerceAtLeast(0))

            btnApplyRole.setOnClickListener {
                onApplyRole(user, spRole.selectedItem.toString())
            }
            btnDetail.setOnClickListener {
                onShowDetail(user)
            }
        }
    }
}
