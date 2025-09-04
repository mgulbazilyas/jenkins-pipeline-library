import groovy.json.JsonOutput

/**
 * Send a simple Discord message for the current Jenkins job.
 *
 * Usage (Pipeline):
 *   discordNotify(text: "Build finished ✅")        // webhook taken from creds 'discordkey'
 *   discordNotify(webhook: "https://discord.com/api/webhooks/...", text: "Hello from Jenkins")
 *   discordNotify(text: "Deployed!", link: "https://example/build/123", title: "Custom Title")
 *
 * Params:
 *   webhook (optional) : Discord webhook URL. If omitted, uses Jenkins Secret Text credential 'discordkey'
 *   text    (required) : Main message text to show
 *   title   (optional) : Embed title (defaults to env.JOB_NAME)
 *   link    (optional) : URL for the title (defaults to env.BUILD_URL)
 *   color   (optional) : Integer RGB decimal (defaults from build result)
 *   username(optional) : Override bot username (defaults "Jenkins")
 *   avatar  (optional) : Avatar URL
 *   footer  (optional) : Footer text (defaults to "Jenkins • <result>")
 */
def call(Map args) {
  if (!args.text) {
    error "discordNotify: 'text' is required"
  }

  // Resolve webhook URL (param > Jenkins credential 'discordkey')
  String webhookUrl = args.webhook
  if (!webhookUrl) {
    withCredentials([string(credentialsId: 'discordkey', variable: 'DISCORD_URL')]) {
      webhookUrl = DISCORD_URL
    }
  }
  if (!webhookUrl) {
    error "discordNotify: No webhook URL provided and credential 'discordkey' not found."
  }

  // Build defaults from current job
  String title = (args.title ?: env.JOB_NAME) ?: "Jenkins Job"
  String link  = (args.link  ?: env.BUILD_URL) ?: ""
  String result = (currentBuild?.currentResult ?: 'UNKNOWN')

  // Pick a color (Discord expects integer)
  Integer color = (args.color instanceof Number) ? args.color as Integer :
                  (result == 'SUCCESS' ? 0x19A974 : // green-ish
                   result == 'UNSTABLE' ? 0xFFD700 : // yellow
                   result == 'ABORTED' ? 0x808080 : // grey
                   0xAC2B37)                         // red for failures/other

  String footer = args.footer ?: "Jenkins • ${result}"
  String username = args.username ?: "Jenkins"
  String avatar   = args.avatar ?: "https://get.jenkins.io/art/jenkins-logo/1024x1024/headshot.png"

  // Discord payload
  Map payload = [
    username  : username,
    avatar_url: avatar,
    embeds    : [[
      title      : title,
      url        : link,
      description: args.text,
      color      : color,
      footer     : [text: footer]
    ]]
  ]

  // Send
  def body = JsonOutput.toJson(payload)
  def resp = httpRequest(
    httpMode: 'POST',
    url: webhookUrl,
    contentType: 'APPLICATION_JSON',
    requestBody: body,
    validResponseCodes: '200:299'
  )

  return resp?.status
}