# Local UI only. Displays the proxy token, never the Groq API key.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$secure = Import-Clixml 'G:\DevServer\secrets\tvflow-groq\proxy-token.xml'
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $form = New-Object Windows.Forms.Form
    $form.Text = 'TVFlow - jeton de passerelle privee'
    $form.Width = 620; $form.Height = 220
    $form.StartPosition = 'CenterScreen'
    $label = New-Object Windows.Forms.Label
    $label.Text = 'Jeton a saisir dans les reglages TVFlow. Ce n est pas la cle Groq.'
    $label.SetBounds(15,15,570,35)
    $box = New-Object Windows.Forms.TextBox
    $box.SetBounds(15,60,570,30); $box.ReadOnly = $true; $box.UseSystemPasswordChar = $true
    $box.Text = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $show = New-Object Windows.Forms.CheckBox
    $show.Text = 'Afficher le jeton'; $show.SetBounds(15,100,220,30)
    $show.Add_CheckedChanged({ $box.UseSystemPasswordChar = !$show.Checked })
    $copy = New-Object Windows.Forms.Button
    $copy.Text = 'Copier'; $copy.SetBounds(450,105,130,35)
    $copy.Add_Click({ [Windows.Forms.Clipboard]::SetText($box.Text) })
    $form.Controls.AddRange(@($label,$box,$show,$copy))
    [void]$form.ShowDialog()
    $box.Text = ''; $form.Dispose()
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
